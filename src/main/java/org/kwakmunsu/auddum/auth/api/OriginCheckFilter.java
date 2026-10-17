package org.kwakmunsu.auddum.auth.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * CSRF 대응(인증-중복-참여 §1). 상태를 바꾸는 요청(GET·HEAD·OPTIONS 외)은 {@code Origin} 헤더가 프런트 주소와 같을 때만 받고,
 * 없거나 다르면 403 {@code FORBIDDEN}으로 거절한다. 별도 CSRF 토큰은 쓰지 않는다.
 */
@Slf4j
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final String allowedOrigin;

    /** @param frontendBaseUrl 프런트 주소. 경로가 붙어 있어도 스킴·호스트·포트만 허용 Origin으로 쓴다. 브라우저가 보내는 형태(소문자, 기본 포트 생략)로 맞춘다. */
    public OriginCheckFilter(String frontendBaseUrl) {
        URI uri = URI.create(frontendBaseUrl);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("프런트 주소에 스킴과 호스트가 필요하다: " + frontendBaseUrl);
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean defaultPort = uri.getPort() == -1
            || (scheme.equals("http") && uri.getPort() == 80)
            || (scheme.equals("https") && uri.getPort() == 443);

        this.allowedOrigin = defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + uri.getPort();
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || allowedOrigin.equals(request.getHeader("Origin"))) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("[{}] 허용되지 않은 Origin의 쓰기 요청 (method={}, uri={}, origin={})",
            DefaultErrorCode.FORBIDDEN.name(), request.getMethod(), request.getRequestURI(), request.getHeader("Origin"));

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
