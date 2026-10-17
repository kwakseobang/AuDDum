package org.kwakmunsu.auddum.auth.api;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.kwakmunsu.auddum.auth.application.IssuedTokens;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 토큰 쿠키의 이름·경로·속성(인증-중복-참여 §1). 액세스는 경로 `/`, 리프레시는 `/api/v1/auth`(재발급·로그아웃에만 전송)다.
 * HttpOnly, SameSite=Lax는 고정이고 Secure는 설정(`auddum.auth.cookie-secure`)으로 정한다.
 */
@Component
public class AuthCookies {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";

    private static final String ACCESS_PATH = "/";
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final boolean secure;

    /** @param secure Secure 속성 여부. 운영은 true, http인 로컬은 false */
    public AuthCookies(@Value("${auddum.auth.cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    public void write(HttpServletResponse response, IssuedTokens tokens) {
        add(response, ACCESS_COOKIE, tokens.accessToken(), ACCESS_PATH, JwtTokenProvider.ACCESS_TTL);
        add(response, REFRESH_COOKIE, tokens.refreshToken(), REFRESH_PATH, JwtTokenProvider.REFRESH_TTL);
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS_COOKIE, "", ACCESS_PATH, Duration.ZERO);
        add(response, REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO);
    }

    private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path(path)
            .maxAge(maxAge)
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
