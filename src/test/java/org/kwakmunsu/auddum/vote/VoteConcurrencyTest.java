package org.kwakmunsu.auddum.vote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.CREATED_AT;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.anonymous;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.choice;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.member;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.pollClosingInOneHour;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.ConcurrentRace;
import org.kwakmunsu.auddum.ConcurrentRace.Outcome;
import org.kwakmunsu.auddum.MySqlConcurrencyTestSupport;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.web.AnonCookieName;
import org.kwakmunsu.auddum.vote.application.VoteCommandService;
import org.kwakmunsu.auddum.vote.application.VoteReader;
import org.kwakmunsu.auddum.vote.application.dto.CastResult;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.VoteJpaRepository;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 같은 순간에 들어온 참여 요청을 실제 MySQL에서 확인한다(인증-중복-참여 §3 "동시 요청"·§4 검증 항목 표,
 * 테스트-배포 §1 "실제 MySQL로 한 번 더 확인"). 진 쪽은 선택이 같아도 409 ALREADY_VOTED이고 재조회하지 않으며,
 * 재전송 200은 없다(설계-결정 §3). 서비스 호출 성공은 API의 201, ALREADY_VOTED는 409다.
 * 유니크 위반 번역 자체는 VoteWriterConflictTest(H2)가 다루고, 여기서는 스레드 경합만 본다.
 */
@AutoConfigureMockMvc
@RequiredArgsConstructor
class VoteConcurrencyTest extends MySqlConcurrencyTestSupport {

    /** 타이밍에 따라 순차 처리로 우연히 통과하는 일을 줄이려고 경합 테스트를 반복한다. */
    static final int REPETITIONS = 5;
    static final String ALLOWED_ORIGIN = "http://localhost:3000";

    final VoteCommandService voteCommandService;
    final VoteReader voteReader;
    final AnonCookieProvider anonCookieProvider;
    final JdbcTemplate jdbcTemplate;
    final VoteJpaRepository voteJpaRepository;
    final MockMvcTester mvcTester;

    @RepeatedTest(REPETITIONS)
    void 같은_회원이_동시에_참여하면_기록은_한_건이고_한_요청만_성공하며_나머지는_ALREADY_VOTED다() throws Exception {
        var poll = saveOpenPoll();
        var memberId = saveMember();

        var outcomes = ConcurrentRace.run(repeat(() -> voteCommandService.castVote(choice(poll, 1), member(memberId))));

        assertThat(voteJpaRepository.count()).isEqualTo(1);
        assertThat(voteReader.find(poll, Voter.member(memberId))).isPresent();
        assertThat(outcomes).hasSize(THREADS);
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertAllLosersAlreadyVoted(outcomes);
    }

    @RepeatedTest(REPETITIONS)
    void 같은_회원이_서로_다른_선택으로_동시에_참여하면_이긴_요청의_선택만_저장된다() throws Exception {
        var poll = saveOpenPoll();
        var memberId = saveMember();
        List<Callable<CastResult>> tasks = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            var position = index % 2 + 1;
            tasks.add(() -> voteCommandService.castVote(choice(poll, position), member(memberId)));
        }

        var outcomes = ConcurrentRace.run(tasks);

        var winner = outcomes.stream().filter(Outcome::succeeded).toList();
        assertThat(winner).hasSize(1);
        assertAllLosersAlreadyVoted(outcomes);
        assertThat(voteReader.findChoice(poll, Voter.member(memberId))).contains(winner.get(0).value().myChoice());
    }

    @RepeatedTest(REPETITIONS)
    void 같은_익명_쿠키로_동시에_참여하면_기록은_한_건이고_한_요청만_성공한다() throws Exception {
        var poll = saveOpenPoll();
        var cookie = anonCookieProvider.issue();

        var outcomes = ConcurrentRace.run(repeat(() -> voteCommandService.castVote(choice(poll, 1), anonymous(cookie.value()))));

        assertThat(voteJpaRepository.count()).isEqualTo(1);
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertAllLosersAlreadyVoted(outcomes);
    }

    @RepeatedTest(REPETITIONS)
    void 같은_익명_쿠키로_동시에_요청하면_HTTP_응답은_201이_하나이고_나머지는_409_ALREADY_VOTED다() throws Exception {
        var poll = saveOpenPoll();
        var cookie = anonCookieProvider.issue();

        var statuses = ConcurrentRace.run(repeat(() -> postVote(poll, cookie.value())));

        var codes = statuses.stream().map(Outcome::value).toList();
        assertThat(codes).filteredOn(code -> code.status() == 201).hasSize(1);
        assertThat(codes).filteredOn(code -> code.status() == 409).hasSize(THREADS - 1)
            .allMatch(code -> code.body().contains("ALREADY_VOTED"));
        assertThat(voteJpaRepository.count()).isEqualTo(1);
    }

    @RepeatedTest(REPETITIONS)
    void 서로_다른_신원이_동시에_참여하면_모두_성공하고_선택지별_득표_합계가_전체_참여_수와_같다() throws Exception {
        var poll = saveOpenPoll();
        List<Callable<CastResult>> tasks = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            var position = index % 2 + 1;
            if (index % 2 == 0) {
                var memberId = saveMember();
                tasks.add(() -> voteCommandService.castVote(choice(poll, position), member(memberId)));
            } else {
                var cookieValue = anonCookieProvider.issue().value();
                tasks.add(() -> voteCommandService.castVote(choice(poll, position), anonymous(cookieValue)));
            }
        }

        var outcomes = ConcurrentRace.run(tasks);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        var tally = voteReader.tally(poll);
        assertThat(tally.total()).isEqualTo(THREADS);
        assertThat(tally.options().stream().mapToLong(option -> option.count()).sum()).isEqualTo(tally.total());
        assertThat(tally.options()).extracting(option -> option.count()).containsExactly(THREADS / 2L, THREADS / 2L);
        assertThat(voteJpaRepository.count()).isEqualTo(THREADS);
    }

    @Test
    void 익명_식별자는_MySQL의_CHAR_36_컬럼에_저장되고_같은_값으로_조회된다() {
        var poll = saveOpenPoll();
        var anonId = new AnonId(anonCookieProvider.issue().id());

        voteJpaRepository.saveAndFlush(Vote.cast(poll, 1, Voter.anonymous(anonId), CREATED_AT));

        var column = jdbcTemplate.queryForMap("""
            SELECT DATA_TYPE AS data_type, CHARACTER_MAXIMUM_LENGTH AS max_length
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vote' AND COLUMN_NAME = 'anon_id'
            """);
        assertThat(column.get("data_type").toString()).isEqualToIgnoringCase("char");
        assertThat(((Number) column.get("max_length")).longValue()).isEqualTo(36);
        var stored = jdbcTemplate.queryForObject("SELECT anon_id FROM vote WHERE poll_id = ?", String.class, poll.getId());
        assertThat(stored).isEqualTo(anonId.value().toString());
        assertThat(voteReader.find(poll, Voter.anonymous(new AnonId(anonId.value())))).isPresent();
    }

    private record HttpOutcome(int status, String body) {

    }

    private HttpOutcome postVote(Poll poll, String cookieValue) {
        var response = mvcTester.post()
            .uri("/api/v1/polls/" + poll.getPublicId().value() + "/votes")
            .header("Origin", ALLOWED_ORIGIN)
            .cookie(new Cookie(AnonCookieName.VALUE, cookieValue))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange()
            .getResponse();
        try {
            return new HttpOutcome(response.getStatus(), response.getContentAsString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertAllLosersAlreadyVoted(List<? extends Outcome<?>> outcomes) {
        var losers = outcomes.stream().filter(outcome -> !outcome.succeeded()).map(Outcome::error).toList();
        assertThat(losers).hasSize(outcomes.size() - 1)
            .allSatisfy(error -> assertThat(error)
                .isInstanceOf(AuddumException.class)
                .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage()));
    }

    private static <T> List<Callable<T>> repeat(Callable<T> task) {
        List<Callable<T>> tasks = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            tasks.add(task);
        }

        return tasks;
    }

    private Poll saveOpenPoll() {
        return savePoll(pollClosingInOneHour(saveMember()));
    }

}
