package org.kwakmunsu.auddum.vote.api;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 익명 식별 쿠키의 이름·속성(인증-중복-참여 §2). 영속 쿠키(1년)이고 HttpOnly, SameSite=Lax, 경로 `/`는 고정이다.
 * Secure는 토큰 쿠키와 같이 설정(`auddum.auth.cookie-secure`)으로 정한다. 운영은 true, http인 로컬은 false다.
 */
@Component
public class AnonCookies {

    public static final String ANON_COOKIE = "auddum_anon";

    private static final Duration MAX_AGE = Duration.ofDays(365);

    private final boolean secure;

    /** @param secure Secure 속성 여부 */
    public AnonCookies(@Value("${auddum.auth.cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    public void write(HttpServletResponse response, String value) {
        ResponseCookie cookie = ResponseCookie.from(ANON_COOKIE, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(MAX_AGE)
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
