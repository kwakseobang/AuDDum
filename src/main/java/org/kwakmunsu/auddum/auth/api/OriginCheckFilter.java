package org.kwakmunsu.auddum.auth.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * CSRF 대응(인증-중복-참여 §1). 상태를 바꾸는 요청(GET·HEAD·OPTIONS 외)은 {@code Origin} 헤더가 허용 목록에 있을 때만 받고,
 * 없거나 목록 밖이면 403 {@code FORBIDDEN}으로 거절한다. 별도 CSRF 토큰은 쓰지 않는다. CORS 설정과 같은 목록을 쓴다.
 */
@Slf4j
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final Set<String> allowedOrigins;

    public OriginCheckFilter(String frontendBaseUrl) {
        this(List.of(frontendBaseUrl));
    }

    /** @param allowedUrls 허용할 프런트 주소들. 각각 {@link #toOrigin}으로 Origin 형태로 맞춘다. */
    public OriginCheckFilter(List<String> allowedUrls) {
        this.allowedOrigins = allowedUrls.stream()
            .map(OriginCheckFilter::toOrigin)
            .collect(Collectors.toUnmodifiableSet());
    }

    /** 주소에서 스킴·호스트·포트만 꺼내 브라우저가 보내는 Origin 형태(소문자, 기본 포트 생략)로 맞춘다. 경로가 붙어 있어도 무시한다. */
    public static String toOrigin(String url) {
        URI uri = URI.create(url);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("프런트 주소에 스킴과 호스트가 필요하다: " + url);
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean defaultPort = uri.getPort() == -1
            || (scheme.equals("http") && uri.getPort() == 80)
            || (scheme.equals("https") && uri.getPort() == 443);

        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + uri.getPort();
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (SAFE_METHODS.contains(request.getMethod()) || (origin != null && allowedOrigins.contains(origin))) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("[OriginCheckFilter] 허용되지 않은 Origin의 쓰기 요청 차단 - method={}, uri={}, origin={}",
            request.getMethod(), request.getRequestURI(), request.getHeader("Origin"));

        response.setStatus(DefaultErrorCode.FORBIDDEN.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON_MAPPER.writeValueAsString(Map.of(
            "status", DefaultErrorCode.FORBIDDEN.getStatus().value(),
            "title", "Forbidden",
            "detail", DefaultErrorCode.FORBIDDEN.getMessage(),
            "instance", request.getRequestURI(),
            "code", DefaultErrorCode.FORBIDDEN.name())));
    }

}
