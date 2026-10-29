package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.auth.application.AuddumOAuth2User;
import org.kwakmunsu.auddum.auth.application.RedirectPathValidator;
import org.kwakmunsu.auddum.auth.application.IssuedTokens;
import org.kwakmunsu.auddum.auth.application.RefreshTokenService;
import org.kwakmunsu.auddum.member.domain.Role;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

/**
 * SuccessHandler: 토큰 쿠키를 발급하고, 돌아갈 경로를 검증한 뒤 302한다. 허용되지 않으면 홈.
 * 가정한 이름: OAuth2LoginSuccessHandler(RefreshTokenService, AuthCookies, RedirectPathValidator, String frontendBaseUrl),
 * RefreshTokenService#issue(Long memberId, Role role, LocalDateTime now), 세션 키 OAuth2LoginSuccessHandler.REDIRECT_SESSION_KEY.
 * 리다이렉트 위치는 {프런트 기본 주소}{경로}로 가정한다.
 */
class OAuth2LoginSuccessHandlerTest {

    static final String FRONT = "http://localhost:3000";
    static final long MEMBER_ID = 7L;

    final RefreshTokenService refreshTokenService = serviceIssuingTokens();
    final OAuth2LoginSuccessHandler handler = newHandler(FRONT);

    @Test
    void 로그인에_성공하면_회원_ID와_권한으로_토큰을_발급한다() throws Exception {
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(requestWithRedirect("/polls"), response, authentication(Role.MEMBER));

        verify(refreshTokenService).issue(eq(MEMBER_ID), eq(Role.MEMBER), any(LocalDateTime.class));
    }

    @Test
    void 관리자_권한이면_ADMIN_권한으로_토큰을_발급한다() throws Exception {
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(requestWithRedirect("/"), response, authentication(Role.ADMIN));

        verify(refreshTokenService).issue(eq(MEMBER_ID), eq(Role.ADMIN), any(LocalDateTime.class));
    }

    @Test
    void 허용된_내부_경로가_있으면_그_경로로_302한다() throws Exception {
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(requestWithRedirect("/polls/AbC123xyz012"), response, authentication(Role.MEMBER));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/polls/AbC123xyz012");
    }

    @Test
    void 돌아갈_경로가_없으면_홈으로_302한다() throws Exception {
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication(Role.MEMBER));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/");
    }

    @Test
    void 프런트_주소_끝에_슬래시가_있어도_경로가_겹치지_않는다() throws Exception {
        var response = new MockHttpServletResponse();
        var slashHandler = newHandler(FRONT + "/");

        slashHandler.onAuthenticationSuccess(requestWithRedirect("/polls"), response, authentication(Role.MEMBER));

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/polls");
    }

    @Test
    void 로그인이_끝나면_로그인_과정에서_쓴_세션을_버린다() throws Exception {
        var request = requestWithRedirect("/polls");
        var session = (MockHttpSession) request.getSession(false);

        handler.onAuthenticationSuccess(request, new MockHttpServletResponse(), authentication(Role.MEMBER));

        assertThat(session.isInvalid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.com", "//evil.com", "/\\evil.com", "javascript:alert(1)", "/a\r\nSet-Cookie: x=y"})
    void 허용되지_않는_돌아갈_경로는_홈으로_302하되_토큰은_발급한다(String redirect) throws Exception {
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(requestWithRedirect(redirect), response, authentication(Role.MEMBER));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/");
        verify(refreshTokenService).issue(eq(MEMBER_ID), eq(Role.MEMBER), any(LocalDateTime.class));
    }

    private static RefreshTokenService serviceIssuingTokens() {
        var service = mock(RefreshTokenService.class);
        given(service.issue(any(), any(), any())).willReturn(new IssuedTokens("access", "refresh"));

        return service;
    }

    private OAuth2LoginSuccessHandler newHandler(String frontendBaseUrl) {
        return new OAuth2LoginSuccessHandler(refreshTokenService, new AuthCookies(true), new RedirectPathValidator(), frontendBaseUrl);
    }

    private MockHttpServletRequest requestWithRedirect(String redirect) {
        var request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(OAuth2LoginSuccessHandler.REDIRECT_SESSION_KEY, redirect);

        return request;
    }

    private OAuth2AuthenticationToken authentication(Role role) {
        var principal = new AuddumOAuth2User(MEMBER_ID, role);

        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao");
    }

}
