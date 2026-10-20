package org.kwakmunsu.auddum.vote.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.vote.api.AnonCookies.ANON_COOKIE;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.expire;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /api/v1/polls/{pollId}/votes 를 실제 필터 체인·서비스·DB로 확인한다. 인증-중복-참여 §2 "요청이 오류로 끝나면
 * (INVALID_OPTION, POLL_CLOSED 등) 새로 발급한 쿠키도 내리지 않는다"를 새 익명 사용자로 고정한다.
 * 쓰기 요청이라 허용 Origin 헤더가 필요하다(인증-중복-참여 §1).
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class VoteCastChainTest extends VoteIntegrationTestSupport {

    static final String ALLOWED_ORIGIN = "http://localhost:3000";

    final MockMvcTester mvcTester;

    @Test
    void 쿠키_없는_새_익명이_존재하지_않는_선택지로_참여하면_400_INVALID_OPTION이다() {
        var poll = saveOpenPoll();

        var result = post(poll, 99);

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_OPTION");
    }

    @Test
    void 쿠키_없는_새_익명이_존재하지_않는_선택지로_참여해도_Set_Cookie가_없다() {
        var poll = saveOpenPoll();

        var result = post(poll, 99);

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 존재하지_않는_선택지로_참여해도_투표_기록이_남지_않는다() {
        var poll = saveOpenPoll();
        var before = voteJpaRepository.count();

        post(poll, 99);

        assertThat(voteJpaRepository.count()).isEqualTo(before);
    }

    @Test
    void 쿠키_없는_새_익명이_마감된_투표에_참여하면_409_POLL_CLOSED다() {
        var poll = saveClosedPoll();

        var result = post(poll, 1);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_CLOSED");
    }

    @Test
    void 쿠키_없는_새_익명이_마감된_투표에_참여해도_Set_Cookie가_없다() {
        var poll = saveClosedPoll();

        var result = post(poll, 1);

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 쿠키_없는_새_익명이_정상_참여하면_201이고_Set_Cookie를_내린다() {
        var poll = saveOpenPoll();

        var result = post(poll, 1);

        assertThat(result).hasStatus(201);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .hasSize(1)
            .allMatch(cookie -> cookie.startsWith(ANON_COOKIE + "="));
    }

    private MvcTestResult post(Poll poll, int position) {
        return mvcTester.post()
            .uri("/api/v1/polls/" + poll.getPublicId().value() + "/votes")
            .header("Origin", ALLOWED_ORIGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": " + position + "}")
            .exchange();
    }

    private Poll saveOpenPoll() {
        return pollJpaRepository.saveAndFlush(Poll.create(saveMember(), "점심 뭐 먹지?", Category.ETC,
            List.of("국밥", "파스타"), null, now()));
    }

    /** 마감 시각은 만들 때 미래여야 하므로 만든 뒤 과거로 돌린다. */
    private Poll saveClosedPoll() {
        var poll = savePoll(Poll.create(saveMember(), "점심 뭐 먹지?", Category.ETC,
            List.of("국밥", "파스타"), now().plusSeconds(1), now()));

        return savePoll(expire(poll));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(TimeConfig.KST);
    }

}
