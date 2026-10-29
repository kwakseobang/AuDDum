package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.WebApiTestSupport;
import org.kwakmunsu.auddum.auth.application.IssuedTokens;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.http.HttpHeaders;

/**
 * POST /auth/refresh 204 + 새 액세스·리프레시 쿠키, 무효면 401 UNAUTHENTICATED. POST /auth/logout 204 + 쿠키 삭제.
 * 가정한 이름: AuthWebApi(경로 /api/v1/auth), RefreshTokenService#reissue(String, LocalDateTime)/#logout(String, LocalDateTime), IssuedTokens(accessToken, refreshToken).
 * 컨트롤러가 쿠키 이름 access_token·refresh_token과 설계서의 경로·속성으로 내린다고 본다.
 * 로그아웃의 로그인 회원 인증(액세스 쿠키)은 슬라이스에서 구성이 정해지지 않아 여기서 확인하지 않는다(note 사용자 질문 참고).
 */
class AuthWebApiTest extends WebApiTestSupport {

    @Test
    void 재발급하면_204와_새_액세스_리프레시_쿠키를_내린다() {
        given(refreshTokenService.reissue(eq("old-refresh"), any())).willReturn(new IssuedTokens("new-access", "new-refresh"));

        var result = mvcTester.post().uri("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "old-refresh")).exchange();

        assertThat(result).hasStatus(204);
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anyMatch(c -> c.startsWith("access_token=new-access") && c.contains("Path=/;") && c.contains("Max-Age=1800"));
        assertThat(cookies).anyMatch(c -> c.startsWith("refresh_token=new-refresh") && c.contains("Path=/api/v1/auth") && c.contains("Max-Age=1209600"));
    }

    @Test
    void 서비스가_재사용을_거절하면_401_UNAUTHENTICATED다() {
        given(refreshTokenService.reissue(eq("reused"), any())).willThrow(new AuddumException(DefaultErrorCode.UNAUTHENTICATED));

        var result = mvcTester.post().uri("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "reused")).exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void 재발급이_401이면_쿠키를_지우지_않아_Set_Cookie_헤더가_없다() {
        given(refreshTokenService.reissue(eq("rejected"), any())).willThrow(new AuddumException(DefaultErrorCode.UNAUTHENTICATED));

        var result = mvcTester.post().uri("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "rejected")).exchange();

        assertThat(result).hasStatus(401);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 로그아웃하면_현재_기기의_토큰을_폐기하고_쿠키를_지운다() {
        var result = mvcTester.post().uri("/api/v1/auth/logout").cookie(new Cookie("refresh_token", "current")).exchange();

        assertThat(result).hasStatus(204);
        verify(refreshTokenService).logout(eq("current"), any());
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anyMatch(c -> c.startsWith("access_token=") && c.contains("Max-Age=0"));
        assertThat(cookies).anyMatch(c -> c.startsWith("refresh_token=") && c.contains("Max-Age=0") && c.contains("Path=/api/v1/auth"));
    }

    @Test
    void 쿠키_없이_로그아웃해도_204이고_두_쿠키를_지운다() {
        var result = mvcTester.post().uri("/api/v1/auth/logout").exchange();

        assertThat(result).hasStatus(204);
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anyMatch(c -> c.startsWith("access_token=") && c.contains("Max-Age=0"));
        assertThat(cookies).anyMatch(c -> c.startsWith("refresh_token=") && c.contains("Max-Age=0") && c.contains("Path=/api/v1/auth"));
    }

}
