package org.kwakmunsu.auddum.auth.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider.AccessClaims;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 액세스 토큰 쿠키의 서명·만료만 확인해 요청한 회원의 ID(principal)와 권한을 SecurityContext에 둔다(인증-중복-참여 §1).
 * 회원 상태는 토큰에 없으므로 여기서 보지 않는다. 토큰이 없거나 무효면 아무것도 두지 않아 비회원으로 취급되고,
 * 로그인이 필요한 곳에서 {@code CurrentMemberIdResolver}가 401을 낸다.
 */
@RequiredArgsConstructor
public class AccessTokenFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String token = accessToken(request);
        if (token != null) {
            authenticate(token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            AccessClaims claims = jwtTokenProvider.parseAccessToken(token, LocalDateTime.now(TimeConfig.KST));
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                claims.memberId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException e) {
            // 무효한 토큰은 비회원으로 취급한다. 토큰 문제로 공개 요청까지 500이 되지 않게 한다.
        }
    }

    private String accessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (AuthCookies.ACCESS_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }

        return null;
    }

}
