package org.kwakmunsu.auddum.auth.api;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.auth.application.IssuedTokens;
import org.kwakmunsu.auddum.auth.application.RefreshTokenService;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@WebApi
public class AuthWebApi extends AuthWebApiDocs {

    private final RefreshTokenService refreshTokenService;
    private final AuthCookies authCookies;

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
        @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletResponse response
    ) {
        IssuedTokens reissued = refreshTokenService.reissue(refreshToken, LocalDateTime.now(TimeConfig.KST));
        authCookies.write(response, reissued);

        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletResponse response
    ) {
        refreshTokenService.logout(refreshToken, LocalDateTime.now(TimeConfig.KST));
        authCookies.clear(response);

        return ResponseEntity.noContent().build();
    }

}
