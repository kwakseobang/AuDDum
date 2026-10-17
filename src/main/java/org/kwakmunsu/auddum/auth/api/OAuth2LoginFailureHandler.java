package org.kwakmunsu.auddum.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.client.RestClientException;

/**
 * 소셜 로그인 실패 처리. 브라우저가 이동 중이라 JSON 대신 프런트 오류 화면으로 302하고, 실패 종류를 code로 알려 준다.
 * 이 경로에서는 회원을 만들지 않는다.
 */
@Slf4j
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    static final String INVALID_STATE = "INVALID_STATE";
    static final String SOCIAL_AUTH_FAILED = "SOCIAL_AUTH_FAILED";
    static final String SOCIAL_UNAVAILABLE = "SOCIAL_UNAVAILABLE";
    static final String ACCESS_DENIED = "ACCESS_DENIED";

    private final String errorPageUrl;

    public OAuth2LoginFailureHandler(String frontendBaseUrl) {
        this.errorPageUrl = frontendBaseUrl + "/login/error?code=";
    }

    @Override
    public void onAuthenticationFailure(
        @NonNull HttpServletRequest request,
        HttpServletResponse response,
        @NonNull AuthenticationException exception
    ) throws IOException {
        String code = resolveCode(exception);
        log.info("[소셜 로그인 실패] code={}, reason={}", code, exception.getMessage());

        response.sendRedirect(errorPageUrl + code);
    }

    private String resolveCode(AuthenticationException exception) {
        if (hasCause(exception, RestClientException.class)) {
            return SOCIAL_UNAVAILABLE;
        }
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            return switch (oauth2.getError().getErrorCode()) {
                case "authorization_request_not_found", "invalid_state_parameter" -> INVALID_STATE;
                case "access_denied" -> ACCESS_DENIED;
                default -> SOCIAL_AUTH_FAILED;
            };
        }

        return SOCIAL_AUTH_FAILED;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }

        return false;
    }

}
