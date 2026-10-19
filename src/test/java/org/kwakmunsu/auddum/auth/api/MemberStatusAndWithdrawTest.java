package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 상태 확인과 탈퇴를 실제 요청부터 응답까지 확인한다(인증-중복-참여 §1 회원 상태 확인, API-명세 §2 DELETE /members/me·§7, 예외-정책 P-04·P-05·P-22).
 * 상태 확인이 어디서 일어나든 상관없이 요청과 응답만 본다.
 *
 * 가정한 이름:
 * - DELETE /api/v1/members/me (204, 쿠키 2개 삭제), POST /api/v1/auth/logout, POST /api/v1/polls
 * - 오류 코드 MEMBER_SUSPENDED(403), UNAUTHENTICATED(401)
 * - 정지 회원은 정지 행위가 아직 없어서 Member의 status 필드를 직접 바꿔 만든다.
 *
 * MockMvc 요청은 테스트와 같은 스레드라 @Transactional로 만든 회원이 요청에서 보이고, 끝나면 롤백된다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class MemberStatusAndWithdrawTest extends IntegrationTestSupport {

    static final String ALLOWED_ORIGIN = "http://localhost:3000";
    static final long NOT_EXISTING_MEMBER_ID = 999_999_999L;
    static final String POLL_BODY = """
        {"question": "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?",
         "category": "SOME",
         "options": ["관심 없음", "그냥 바쁜 것"]}
        """;

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;
    final MemberJpaRepository memberJpaRepository;

    @Test
    void 정지_회원의_투표_생성은_403_MEMBER_SUSPENDED다() {
        var memberId = createMember(MemberStatus.SUSPENDED);

        var result = createPoll(memberId);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_SUSPENDED");
    }

    @Test
    void 탈퇴_회원의_투표_생성은_401_UNAUTHENTICATED다() {
        var memberId = createMember(MemberStatus.WITHDRAWN);

        var result = createPoll(memberId);

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void DB에_없는_회원의_투표_생성은_401_UNAUTHENTICATED다() {
        var result = createPoll(NOT_EXISTING_MEMBER_ID);

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void 정지_회원도_로그아웃은_할_수_있다() {
        var memberId = createMember(MemberStatus.SUSPENDED);

        var result = mvcTester.post().uri("/api/v1/auth/logout")
            .header("Origin", ALLOWED_ORIGIN)
            .cookie(accessCookie(memberId))
            .exchange();

        assertThat(result).hasStatus(204);
    }

    @Test
    void 탈퇴한_회원도_액세스_리프레시_쿠키로_로그아웃은_204다() {
        var memberId = createMember(MemberStatus.WITHDRAWN);

        var result = mvcTester.post().uri("/api/v1/auth/logout")
            .header("Origin", ALLOWED_ORIGIN)
            .cookie(accessCookie(memberId))
            .cookie(new Cookie(AuthCookies.REFRESH_COOKIE, "withdrawn-members-refresh-token"))
            .exchange();

        assertThat(result).hasStatus(204);
    }

    @Test
    void 정지_회원도_탈퇴할_수_있고_상태가_WITHDRAWN으로_바뀐다() {
        var memberId = createMember(MemberStatus.SUSPENDED);

        var result = withdraw(memberId);
        memberJpaRepository.flush();

        assertThat(result).hasStatus(204);
        assertThat(memberJpaRepository.findById(memberId).orElseThrow().getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
    }

    @Test
    void 정상_회원이_탈퇴하면_204이고_액세스_리프레시_쿠키를_모두_지운다() {
        var memberId = createMember(MemberStatus.ACTIVE);

        var result = withdraw(memberId);

        assertThat(result).hasStatus(204);
        var setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anySatisfy(cookie -> assertThat(cookie).startsWith(AuthCookies.ACCESS_COOKIE + "=;")
            .contains("Max-Age=0").contains("Path=/;"));
        assertThat(setCookies).anySatisfy(cookie -> assertThat(cookie).startsWith(AuthCookies.REFRESH_COOKIE + "=;")
            .contains("Max-Age=0").contains("Path=/api/v1/auth"));
    }

    @Test
    void 탈퇴한_뒤에는_같은_액세스_토큰으로_쓰기_요청을_하면_401이다() {
        var memberId = createMember(MemberStatus.ACTIVE);
        assertThat(withdraw(memberId)).hasStatus(204);

        var result = createPoll(memberId);

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void 탈퇴한_뒤에_같은_액세스_토큰으로_다시_탈퇴하면_401이다() {
        var memberId = createMember(MemberStatus.ACTIVE);
        assertThat(withdraw(memberId)).hasStatus(204);

        var result = withdraw(memberId);

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void 조회_요청은_정지_회원이어도_상태를_확인하지_않고_통과한다() {
        var memberId = createMember(MemberStatus.SUSPENDED);

        var result = mvcTester.get().uri("/api/v1/polls").cookie(accessCookie(memberId)).exchange();

        assertThat(result).hasStatus(200);
    }

    @Test
    void 조회_요청은_탈퇴_회원이나_없는_회원의_토큰이어도_상태를_확인하지_않고_통과한다() {
        var withdrawnId = createMember(MemberStatus.WITHDRAWN);

        assertThat(mvcTester.get().uri("/api/v1/polls").cookie(accessCookie(withdrawnId)).exchange()).hasStatus(200);
        assertThat(mvcTester.get().uri("/api/v1/polls").cookie(accessCookie(NOT_EXISTING_MEMBER_ID)).exchange()).hasStatus(200);
    }

    private MvcTestResult createPoll(Long memberId) {
        return mvcTester.post().uri("/api/v1/polls")
            .header("Origin", ALLOWED_ORIGIN)
            .cookie(accessCookie(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(POLL_BODY)
            .exchange();
    }

    private MvcTestResult withdraw(Long memberId) {
        return mvcTester.delete().uri("/api/v1/members/me")
            .header("Origin", ALLOWED_ORIGIN)
            .cookie(accessCookie(memberId))
            .exchange();
    }

    private Long createMember(MemberStatus status) {
        var member = Member.signUp();
        MemberFixture.changeStatus(member, status);

        return memberJpaRepository.saveAndFlush(member).getId();
    }

    private Cookie accessCookie(Long memberId) {
        LocalDateTime now = LocalDateTime.now(TimeConfig.KST);

        return new Cookie(AuthCookies.ACCESS_COOKIE, jwtTokenProvider.createAccessToken(memberId, Role.MEMBER, now));
    }

}
