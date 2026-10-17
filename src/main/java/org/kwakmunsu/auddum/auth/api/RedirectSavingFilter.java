package org.kwakmunsu.auddum.auth.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 소셜 로그인 시작 요청(`/oauth2/authorization/{제공자}`)의 `redirect` 파라미터를 세션에 보관한다. 값이 안전한지는 로그인이 끝난 뒤 성공 핸들러가 검증한다.
 */
public class RedirectSavingFilter extends OncePerRequestFilter {

    static final String LOGIN_START_PATH_PREFIX = "/oauth2/authorization/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(LOGIN_START_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String redirect = request.getParameter("redirect");
        if (redirect != null) {
            request.getSession().setAttribute(OAuth2LoginSuccessHandler.REDIRECT_SESSION_KEY, redirect);
        }

        filterChain.doFilter(request, response);
    }

}
