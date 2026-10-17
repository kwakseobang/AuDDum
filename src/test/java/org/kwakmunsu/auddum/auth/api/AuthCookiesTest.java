package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.auth.application.IssuedTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

/** 인증-중복-참여 §1 쿠키: 이름·경로·속성·수명. 값(JWT)은 확인하지 않는다. */
class AuthCookiesTest {

    static final IssuedTokens TOKENS = new IssuedTokens("access", "refresh");

    final AuthCookies cookies = new AuthCookies(true);

    @Test
    void 액세스와_리프레시_토큰_쿠키를_설계서의_경로와_속성으로_내린다() {
        var response = new MockHttpServletResponse();

        cookies.write(response, TOKENS);

        List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(headers).hasSize(2);
        assertThat(headers.get(0)).startsWith("access_token=")
            .contains("Path=/;", "Max-Age=1800", "HttpOnly", "Secure", "SameSite=Lax");
        assertThat(headers.get(1)).startsWith("refresh_token=")
            .contains("Path=/api/v1/auth", "Max-Age=1209600", "HttpOnly", "Secure", "SameSite=Lax");
    }

    @Test
    void secure가_꺼져_있으면_Secure_속성을_붙이지_않는다() {
        var response = new MockHttpServletResponse();

        new AuthCookies(false).write(response, TOKENS);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).noneMatch(cookie -> cookie.contains("Secure"));
    }

}
