package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * API-명세 FailureHandler + brief 확정: 실패는 JSON이 아니라 프런트 오류 화면으로 302하며 코드를 전달한다.
 * 가정한 이름: OAuth2LoginFailureHandler(String frontendBaseUrl), 이동 위치 {프런트}/login/error?code=<코드>.
 * 회원은 핸들러가 만들지 않는다(회원 생성은 성공한 OAuth2UserService 호출에서만 일어난다).
 * Spring이 던지는 OAuth2 오류 코드와 우리 코드의 대응은 사용자 확인 대상이다(note 참조).
 */
class OAuth2LoginFailureHandlerTest {

    static final String FRONT = "http://localhost:3000";

    final OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(FRONT);

    @ParameterizedTest
    @MethodSource("실패_원인과_오류_코드")
    void 로그인_실패는_원인에_맞는_코드로_JSON_본문_없이_오류_화면에_302한다(AuthenticationException exception, String code) throws Exception {
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/login/error?code=" + code);
        assertThat(response.getContentAsString()).isEmpty();
    }

    static Stream<Arguments> 실패_원인과_오류_코드() {
        return Stream.of(
            // state가 없거나 일치하지 않는다
            Arguments.of(oauthError("authorization_request_not_found"), "INVALID_STATE"),
            Arguments.of(oauthError("invalid_state_parameter"), "INVALID_STATE"),
            // 인가 코드가 만료되거나 재사용되었다
            Arguments.of(oauthError("invalid_grant"), "SOCIAL_AUTH_FAILED"),
            // 카카오 장애나 타임아웃이다
            Arguments.of(new OAuth2AuthenticationException(new OAuth2Error("invalid_token_response"), new ResourceAccessException("timeout")),
                "SOCIAL_UNAVAILABLE"),
            // 카카오가 4xx로 거절한 인가 코드 실패는 장애가 아니라 인증 실패다
            Arguments.of(new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"),
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null)),
                "SOCIAL_AUTH_FAILED")
        );
    }

    @Test
    void 로그인_과정에서_쓴_세션은_실패해도_버린다() throws Exception {
        var request = new MockHttpServletRequest();
        var session = (MockHttpSession) request.getSession(true);

        handler.onAuthenticationFailure(request, new MockHttpServletResponse(), oauthError("invalid_grant"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 프런트_주소_끝에_슬래시가_있어도_경로가_겹치지_않는다() throws Exception {
        var response = new MockHttpServletResponse();

        new OAuth2LoginFailureHandler(FRONT + "/").onAuthenticationFailure(new MockHttpServletRequest(), response, oauthError("invalid_grant"));

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/login/error?code=SOCIAL_AUTH_FAILED");
    }

    @Test
    void 사용자가_카카오_화면에서_취소해도_가입_없이_오류_화면으로_302한다() throws Exception {
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, oauthError("access_denied"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).startsWith(FRONT + "/login/error");
    }

    private static OAuth2AuthenticationException oauthError(String errorCode) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode));
    }

}
