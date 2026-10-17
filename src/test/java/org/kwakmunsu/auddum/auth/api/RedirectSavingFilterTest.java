package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** API-명세 §2 로그인 시작: `redirect` 파라미터를 세션에 보관한다. 안전한지는 성공 핸들러가 검증한다. */
class RedirectSavingFilterTest {

    final RedirectSavingFilter filter = new RedirectSavingFilter();

    @Test
    void 로그인_시작_요청의_redirect를_세션에_보관한다() throws Exception {
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
        request.setParameter("redirect", "/polls/AbC123xyz012");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession(false).getAttribute(OAuth2LoginSuccessHandler.REDIRECT_SESSION_KEY))
            .isEqualTo("/polls/AbC123xyz012");
    }

    @Test
    void redirect가_없는_로그인_시작은_이전_시도의_redirect를_지운다() throws Exception {
        var first = new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
        first.setParameter("redirect", "/polls/AbC123xyz012");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        var second = new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
        second.setSession(first.getSession(false));

        filter.doFilter(second, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(second.getSession(false).getAttribute(OAuth2LoginSuccessHandler.REDIRECT_SESSION_KEY)).isNull();
    }

    @Test
    void 로그인_시작이_아닌_요청은_세션을_만들지_않는다() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/polls");
        request.setParameter("redirect", "/polls");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession(false)).isNull();
    }

}
