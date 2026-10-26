package org.kwakmunsu.auddum.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.report.ReportFixture.ALLOWED_ORIGIN;
import static org.kwakmunsu.auddum.report.ReportFixture.newPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.reasonBody;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.web.AnonCookieName;
import org.kwakmunsu.auddum.vote.ConcurrentRace;
import org.kwakmunsu.auddum.vote.ConcurrentRace.Outcome;
import org.kwakmunsu.auddum.vote.MySqlConcurrencyTestSupport;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 같은 신고자의 동시 중복 신고를 실제 MySQL(Testcontainers, Docker 필요)에서 확인한다.
 * 기준은 예외-정책 P-18·"같은 신고자의 중복 신고 -> 409, 한 건만 인정", API-명세 §5, ERD report 유니크 제약.
 * 인증-중복-참여 §4 표에 신고 전용 행은 없어 참여의 동시성 기대(1건 저장, 진 쪽 409)를 신고에 같은 방식으로 적용한 것이다.
 */
@AutoConfigureMockMvc
@RequiredArgsConstructor
class ReportConcurrencyTest extends MySqlConcurrencyTestSupport {

    static final int REPETITIONS = 5;

    final MockMvcTester mvcTester;
    final JdbcTemplate jdbcTemplate;
    final JwtTokenProvider jwtTokenProvider;
    final AnonCookieProvider anonCookieProvider;

    /** 상위 클래스의 정리보다 먼저 실행되어 report가 poll·member보다 먼저 지워진다. */
    @AfterEach
    void cleanUpReports() {
        jdbcTemplate.update("DELETE FROM report");
    }

    @RepeatedTest(REPETITIONS)
    void 같은_익명_쿠키로_동시에_신고하면_기록은_한_건이고_201이_하나이며_나머지는_409다() throws Exception {
        var poll = savePoll(newPoll(saveMember()));
        var cookie = new Cookie(AnonCookieName.VALUE, anonCookieProvider.issue().value());

        var outcomes = ConcurrentRace.run(repeat(() -> postReport(poll, cookie)));

        assertOneCreatedAndRestConflict(outcomes);
    }

    @RepeatedTest(REPETITIONS)
    void 같은_회원이_동시에_신고하면_기록은_한_건이고_201이_하나이며_나머지는_409다() throws Exception {
        var poll = savePoll(newPoll(saveMember()));
        var memberId = saveMember();
        var cookie = new Cookie(AuthCookies.ACCESS_COOKIE,
            jwtTokenProvider.createAccessToken(memberId, Role.MEMBER, LocalDateTime.now(TimeConfig.KST)));

        var outcomes = ConcurrentRace.run(repeat(() -> postReport(poll, cookie)));

        assertOneCreatedAndRestConflict(outcomes);
    }

    private void assertOneCreatedAndRestConflict(List<Outcome<HttpOutcome>> outcomes) {
        var results = outcomes.stream().map(Outcome::value).toList();
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(results).filteredOn(result -> result.status() == 201).hasSize(1);
        assertThat(results).filteredOn(result -> result.status() == 409).hasSize(THREADS - 1)
            .allMatch(result -> result.body().contains("REPORT_ALREADY_SUBMITTED"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM report", String.class)).isEqualTo("PENDING");
    }

    private List<Callable<HttpOutcome>> repeat(Callable<HttpOutcome> task) {
        return Stream.generate(() -> task).limit(THREADS).toList();
    }

    private record HttpOutcome(int status, String body) {

    }

    private HttpOutcome postReport(Poll poll, Cookie cookie) throws Exception {
        var response = mvcTester.post()
            .uri("/api/v1/polls/" + poll.getPublicId().value() + "/reports")
            .header("Origin", ALLOWED_ORIGIN)
            .cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(reasonBody("SPAM"))
            .exchange()
            .getResponse();

        return new HttpOutcome(response.getStatus(), response.getContentAsString());
    }

}
