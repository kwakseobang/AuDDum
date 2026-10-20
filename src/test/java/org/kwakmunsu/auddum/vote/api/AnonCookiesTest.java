package org.kwakmunsu.auddum.vote.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 익명 식별 쿠키의 속성. 인증-중복-참여 §2: HttpOnly, SameSite=Lax, 경로 `/`, 365일, Secure는 `auddum.auth.cookie-secure`를 따른다.
 */
class AnonCookiesTest {

    private static final String VALUE = "v1.0b9a3c3e-6c1e-4f55-9a43-2f7e3b6d9a10.c2lnbmF0dXJl";

    @Test
    void secure가_true이면_Set_Cookie에_Secure가_있다() {
        var response = new MockHttpServletResponse();

        new AnonCookies(true).write(response, VALUE);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Secure");
    }

    @Test
    void secure가_false이면_Set_Cookie에_Secure가_없다() {
        var response = new MockHttpServletResponse();

        new AnonCookies(false).write(response, VALUE);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Secure");
    }

    @Test
    void secure와_관계없이_나머지_속성은_고정이다() {
        var response = new MockHttpServletResponse();

        new AnonCookies(false).write(response, VALUE);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
            .startsWith("auddum_anon=" + VALUE)
            .contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=" + 365L * 24 * 60 * 60);
    }

}
