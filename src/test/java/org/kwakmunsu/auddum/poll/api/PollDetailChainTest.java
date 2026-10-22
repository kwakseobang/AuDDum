package org.kwakmunsu.auddum.poll.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.vote.api.AnonCookies.ANON_COOKIE;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.choice;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.expire;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.member;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.noCredential;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.tamperSignature;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.application.VoteCommandService;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/v1/polls/{pollId} 를 실제 필터 체인과 DB로 확인한다. 쿠키(토큰, 익명)가 신원으로 이어지고 myChoice·result가 정해지는지,
 * 조회가 Set-Cookie를 내리지 않는지 본다. API-명세 §3·§4, 인증-중복-참여 §2. 규칙의 전수 조합은 PollQueryServiceParticipationTest가 맡는다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class PollDetailChainTest extends VoteIntegrationTestSupport {

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;
    final VoteCommandService voteCommandService;

    @Test
    void 쿠키가_없는_비회원은_미참여라_result가_null이고_participantCount를_받으며_Set_Cookie가_없다() {
        var poll = savePoll(saveMember());
        voteCommandService.castVote(choice(poll, 1), member(saveMember()));

        var result = mvcTester.get().uri(uri(poll)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.myChoice").isNull();
        assertThat(result).bodyJson().extractingPath("$.result").isNull();
        assertThat(result).bodyJson().extractingPath("$.participantCount").isEqualTo(1);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 참여_응답으로_받은_익명_쿠키로_조회하면_myChoice와_result를_받는다() {
        var poll = savePoll(saveMember());
        var cast = voteCommandService.castVote(choice(poll, 2), noCredential());

        var result = mvcTester.get().uri(uri(poll)).cookie(new Cookie(ANON_COOKIE, cast.newCookieValue())).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.myChoice").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.result.total").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.result.options[0].count").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.result.options[1].count").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.participantCount").isEqualTo(1);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 위조_쿠키로_조회하면_200이고_미참여로_보며_Set_Cookie와_투표_기록이_없다() {
        var poll = savePoll(saveMember());
        var cast = voteCommandService.castVote(choice(poll, 1), noCredential());
        var votesBefore = voteJpaRepository.count();
        var forged = tamperSignature(cast.newCookieValue());

        var result = mvcTester.get().uri(uri(poll)).cookie(new Cookie(ANON_COOKIE, forged)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.myChoice").isNull();
        assertThat(result).bodyJson().extractingPath("$.result").isNull();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(voteJpaRepository.count()).isEqualTo(votesBefore);
    }

    @Test
    void 형식이_깨진_쿠키로_조회하면_200이고_Set_Cookie가_없다() {
        var poll = savePoll(saveMember());

        var result = mvcTester.get().uri(uri(poll)).cookie(new Cookie(ANON_COOKIE, "not-a-cookie")).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 빈_쿠키로_조회하면_200이고_Set_Cookie가_없다() {
        var poll = savePoll(saveMember());

        var result = mvcTester.get().uri(uri(poll)).cookie(new Cookie(ANON_COOKIE, "")).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 회원_토큰_쿠키로_조회하면_회원_ID로_참여를_판단한다() {
        var memberId = saveMember();
        var poll = savePoll(saveMember());
        voteCommandService.castVote(choice(poll, 2), member(memberId));

        var result = mvcTester.get().uri(uri(poll)).cookie(accessCookie(memberId)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.myChoice").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.result.total").isEqualTo(1);
    }

    @Test
    void 로그인한_회원은_익명_쿠키를_보지_않는다() {
        var memberId = saveMember();
        var poll = savePoll(saveMember());
        var cast = voteCommandService.castVote(choice(poll, 1), noCredential());

        var result = mvcTester.get().uri(uri(poll))
            .cookie(accessCookie(memberId), new Cookie(ANON_COOKIE, cast.newCookieValue()))
            .exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.myChoice").isNull();
        assertThat(result).bodyJson().extractingPath("$.result").isNull();
    }

    @Test
    void 마감된_투표는_쿠키가_없는_비회원에게도_result를_내린다() {
        var poll = pollJpaRepository.saveAndFlush(Poll.create(saveMember(), "점심 뭐 먹지?", Category.ETC,
            List.of("국밥", "파스타"), LocalDateTime.now(TimeConfig.KST).plusSeconds(1), LocalDateTime.now(TimeConfig.KST)));
        voteCommandService.castVote(choice(poll, 1), member(saveMember()));
        pollJpaRepository.saveAndFlush(expire(poll));

        var result = mvcTester.get().uri(uri(poll)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.closed").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.myChoice").isNull();
        assertThat(result).bodyJson().extractingPath("$.result.total").isEqualTo(1);
    }

    @Test
    void 정지_회원도_조회할_수_있다() {
        var suspendedId = saveSuspendedMember();
        var poll = savePoll(saveMember());

        var result = mvcTester.get().uri(uri(poll)).cookie(accessCookie(suspendedId)).exchange();

        assertThat(result).hasStatus(200);
    }

    @Test
    void 일반_사용자에게_참여자가_있는_숨김_투표는_404이고_결과를_담지_않는다() {
        var memberId = saveMember();
        var poll = savePoll(saveMember());
        voteCommandService.castVote(choice(poll, 1), member(memberId));
        poll.hide();
        pollJpaRepository.saveAndFlush(poll);

        var result = mvcTester.get().uri(uri(poll)).cookie(accessCookie(memberId)).exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
        assertThat(result).bodyJson().doesNotHavePath("$.result");
        assertThat(result).bodyJson().doesNotHavePath("$.myChoice");
    }

    @Test
    void 작성자는_숨김_투표를_200으로_조회하고_status가_HIDDEN이다() {
        var authorId = saveMember();
        var poll = savePoll(authorId);
        poll.hide();
        pollJpaRepository.saveAndFlush(poll);

        var result = mvcTester.get().uri(uri(poll)).cookie(accessCookie(authorId)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("HIDDEN");
    }

    @Test
    void ID_형식_오류는_400이다() {
        assertThat(mvcTester.get().uri("/api/v1/polls/short").exchange()).hasStatus(400);
    }

    @Test
    void 없는_ID는_404다() {
        assertThat(mvcTester.get().uri("/api/v1/polls/abcdefghijkl").exchange()).hasStatus(404);
    }

    private static String uri(Poll poll) {
        return "/api/v1/polls/" + poll.getPublicId().value();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(TimeConfig.KST);
    }

    private Poll savePoll(Long authorId) {
        return pollJpaRepository.saveAndFlush(Poll.create(authorId, "점심 뭐 먹지?", Category.ETC,
            List.of("국밥", "파스타"), null, now()));
    }

    private Cookie accessCookie(Long memberId) {
        return new Cookie(AuthCookies.ACCESS_COOKIE, jwtTokenProvider.createAccessToken(memberId, Role.MEMBER, now()));
    }

}
