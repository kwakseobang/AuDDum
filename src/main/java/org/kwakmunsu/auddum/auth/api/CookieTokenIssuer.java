package org.kwakmunsu.auddum.auth.api;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import org.kwakmunsu.auddum.auth.application.TokenIssuer;
import org.kwakmunsu.auddum.member.domain.Role;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * 임시 토큰 발급. 쿠키 이름·경로·속성은 설계서(인증-중복-참여 §1)대로 내리지만 값은 서명된 JWT가 아니라 무작위 문자열이다.
 * 이 값을 읽어 신원을 확인하는 곳은 아직 없다. JWT 발급과 리프레시 토큰 저장은 #13에서 이 구현을 대체한다.
 */
public class CookieTokenIssuer implements TokenIssuer {

    private final boolean secure;

    /** @param secure Secure 속성 여부. 운영은 true, http인 로컬은 false(`auddum.auth.cookie-secure`) */
    public CookieTokenIssuer(boolean secure) {
        this.secure = secure;
    }

    static final String ACCESS_COOKIE = "access_token";
    static final String REFRESH_COOKIE = "refresh_token";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    @Override
    public void issue(Long memberId, Role role, HttpServletResponse response) {
        addCookie(response, ACCESS_COOKIE, "/", ACCESS_TTL);
        addCookie(response, REFRESH_COOKIE, "/api/v1/auth", REFRESH_TTL);
    }

    private void addCookie(HttpServletResponse response, String name, String path, Duration ttl) {
        ResponseCookie cookie = ResponseCookie.from(name, UUID.randomUUID().toString())
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path(path)
            .maxAge(ttl)
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
