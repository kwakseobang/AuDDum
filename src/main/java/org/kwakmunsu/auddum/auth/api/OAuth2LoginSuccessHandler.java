package org.kwakmunsu.auddum.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.kwakmunsu.auddum.auth.application.AuddumOAuth2User;
import org.kwakmunsu.auddum.auth.application.RedirectPathValidator;
import org.kwakmunsu.auddum.auth.application.RefreshTokenService;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * 소셜 로그인 성공 처리. 토큰 쿠키를 발급하고, 로그인 시작 때 세션에 보관한 돌아갈 경로를 검증해 프런트로 302한다.
 * 로그인 상태는 JWT로 유지하므로 로그인 과정에서 쓴 세션은 여기서 버린다.
 */
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    /** 로그인 시작 때 `redirect` 파라미터를 보관하는 세션 키. */
    public static final String REDIRECT_SESSION_KEY = "auddum.login.redirect";

    private final RefreshTokenService refreshTokenService;
    private final AuthCookies authCookies;
    private final RedirectPathValidator redirectPathValidator;
    private final String frontendBaseUrl;

    public OAuth2LoginSuccessHandler(
        RefreshTokenService refreshTokenService,
        AuthCookies authCookies,
        RedirectPathValidator redirectPathValidator,
        String frontendBaseUrl
    ) {
        this.refreshTokenService = refreshTokenService;
        this.authCookies = authCookies;
        this.redirectPathValidator = redirectPathValidator;
        this.frontendBaseUrl = FrontendUrls.normalize(frontendBaseUrl);
    }

    @Override
    public void onAuthenticationSuccess(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        Authentication authentication
    ) throws IOException {
        AuddumOAuth2User user = (AuddumOAuth2User) authentication.getPrincipal();
        Objects.requireNonNull(user);
        authCookies.write(response, refreshTokenService.issue(user.memberId(), user.role(), LocalDateTime.now(TimeConfig.KST)));

        String redirect = popRedirect(request);
        response.sendRedirect(frontendBaseUrl + redirectPathValidator.resolve(redirect));
    }

    private String popRedirect(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        Object redirect = session.getAttribute(REDIRECT_SESSION_KEY);
        session.invalidate();

        return redirect instanceof String path ? path : null;
    }

}
