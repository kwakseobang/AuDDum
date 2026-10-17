package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.client.ResourceAccessException;

/**
 * API-명세 §2 FailureHandler + brief 확정: 실패는 JSON이 아니라 프런트 오류 화면으로 302하며 코드를 전달한다.
 * 가정한 이름: OAuth2LoginFailureHandler(String frontendBaseUrl), 이동 위치 {프런트}/login/error?code=<코드>.
 * 회원은 핸들러가 만들지 않는다(회원 생성은 성공한 OAuth2UserService 호출에서만 일어난다).
 * Spring이 던지는 OAuth2 오류 코드와 우리 코드의 대응은 사용자 확인 대상이다(note 참조).
 */
class OAuth2LoginFailureHandlerTest {

    static final String FRONT = "http://localhost:3000";

    final OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(FRONT);

    @Test
    void state가_없거나_일치하지_않으면_INVALID_STATE_코드로_오류_화면에_302한다() throws Exception {
        assertRedirectsWithCode(oauthError("authorization_request_not_found"), "INVALID_STATE");
        assertRedirectsWithCode(oauthError("invalid_state_parameter"), "INVALID_STATE");
    }

    @Test
    void 인가_코드가_만료되거나_재사용되면_SOCIAL_AUTH_FAILED_코드로_오류_화면에_302한다() throws Exception {
        assertRedirectsWithCode(oauthError("invalid_grant"), "SOCIAL_AUTH_FAILED");
    }

    @Test
    void 카카오_장애나_타임아웃이면_SOCIAL_UNAVAILABLE_코드로_오류_화면에_302한다() throws Exception {
        var timeout = new OAuth2AuthenticationException(new OAuth2Error("invalid_token_response"), new ResourceAccessException("timeout"));

        assertRedirectsWithCode(timeout, "SOCIAL_UNAVAILABLE");
    }

    @Test
    void 실패_응답은_JSON_본문이_없는_리다이렉트다() throws Exception {
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, oauthError("invalid_grant"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void 사용자가_카카오_화면에서_취소해도_가입_없이_오류_화면으로_302한다() throws Exception {
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, oauthError("access_denied"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).startsWith(FRONT + "/login/error");
    }

    private void assertRedirectsWithCode(AuthenticationException exception, String code) throws Exception {
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/login/error?code=" + code);
    }

    private OAuth2AuthenticationException oauthError(String errorCode) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode));
    }

}
