package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 SecurityConfig 체인의 배선 확인(인증-중복-참여 CSRF·조회, API-명세 FORBIDDEN·UNAUTHENTICATED).
 * 필터 단위 테스트는 따로 있고, 여기서는 Origin 검사 -> 액세스 토큰 -> 컨트롤러 순서로 체인에 붙어 있는지만 본다.
 * MockMvc 요청은 테스트와 같은 스레드라 @Transactional로 만든 회원이 요청에서 보이고, 끝나면 롤백된다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class SecurityFilterChainTest extends IntegrationTestSupport {

    static final String ALLOWED_ORIGIN = "http://localhost:3000";
    static final String REQUEST_BODY = """
        {"question": "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?",
         "category": "SOME",
         "options": ["관심 없음", "그냥 바쁜 것"]}
        """;

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;
    final MemberJpaRepository memberJpaRepository;

    @Test
    void Origin_없는_쓰기_요청은_체인에서_403_FORBIDDEN이다() {
        var memberId = memberJpaRepository.save(Member.signUp()).getId();

        var result = mvcTester.post().uri("/api/v1/polls")
            .cookie(accessCookie(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY)
            .exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void 유효한_액세스_쿠키와_허용_Origin이면_인증을_통과해_생성된다() {
        var memberId = memberJpaRepository.save(Member.signUp()).getId();

        var result = mvcTester.post().uri("/api/v1/polls")
            .header("Origin", ALLOWED_ORIGIN)
            .cookie(accessCookie(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY)
            .exchange();

        assertThat(result).hasStatus(201);
    }

    @Test
    void 쿠키_없이_허용_Origin으로_요청하면_401_UNAUTHENTICATED다() {
        var result = mvcTester.post().uri("/api/v1/polls")
            .header("Origin", ALLOWED_ORIGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY)
            .exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    private Cookie accessCookie(Long memberId) {
        LocalDateTime now = LocalDateTime.now(TimeConfig.KST);

        return new Cookie(AuthCookies.ACCESS_COOKIE, jwtTokenProvider.createAccessToken(memberId, Role.MEMBER, now));
    }

}
