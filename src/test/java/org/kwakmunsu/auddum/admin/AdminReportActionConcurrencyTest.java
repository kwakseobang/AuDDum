package org.kwakmunsu.auddum.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.report.ReportFixture.ALLOWED_ORIGIN;
import static org.kwakmunsu.auddum.report.ReportFixture.newPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newReport;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.RepeatedTest;
import org.kwakmunsu.auddum.ConcurrentRace;
import org.kwakmunsu.auddum.ConcurrentRace.Outcome;
import org.kwakmunsu.auddum.MySqlConcurrencyTestSupport;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.infrastructure.ReportJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 같은 신고에 기각 요청이 동시에 들어오는 경우(더블클릭·재전송)를 실제 MySQL(Testcontainers, Docker 필요)에서 확인한다.
 * 기준은 R-REPORT-08·OP-07. 같은 신고에 요청이 동시에 들어오면(더블클릭·재전송) 한쪽만 성공하고 나머지는 409 REPORT_ALREADY_HANDLED다.
 * 동시 요청은 신고의 낙관적 락(report.version)으로 가린다.
 * 이력은 "한 건만" 남아야 하므로 DISMISS_REPORT 이력 1건을 기대한다. 같은 투표 다른 신고 경쟁은 보장 범위 밖이다.
 */
@AutoConfigureMockMvc
@RequiredArgsConstructor
class AdminReportActionConcurrencyTest extends MySqlConcurrencyTestSupport {

    static final int REPETITIONS = 5;
    static final String BODY = "{\"reason\": \"동시 처리 확인\"}";

    final MockMvcTester mvcTester;
    final JdbcTemplate jdbcTemplate;
    final JwtTokenProvider jwtTokenProvider;
    final ReportJpaRepository reportJpaRepository;

    @RepeatedTest(REPETITIONS)
    void 같은_신고에_기각이_동시에_두_번_들어오면_한쪽만_204이고_나머지는_409다() throws Exception {
        var poll = savePoll(newPoll(saveMember()));
        var report = saveReport(poll.getId());

        var outcomes = ConcurrentRace.run(tasks(List.of(report, report)));

        assertExactlyOneSucceeded(outcomes, 2);
        assertSingleHandling(poll.getId(), List.of(report));
    }

    @RepeatedTest(REPETITIONS)
    void 같은_신고에_기각이_동시에_여러_번_들어와도_한쪽만_204이고_나머지는_409다() throws Exception {
        var poll = savePoll(newPoll(saveMember()));
        var report = saveReport(poll.getId());

        var outcomes = ConcurrentRace.run(tasks(Collections.nCopies(THREADS, report)));

        assertExactlyOneSucceeded(outcomes, THREADS);
        assertSingleHandling(poll.getId(), List.of(report));
    }

    @RepeatedTest(REPETITIONS)
    void 서로_다른_투표의_신고는_동시에_처리해도_모두_204다() throws Exception {
        var polls = IntStream.range(0, 4).mapToObj(i -> savePoll(newPoll(saveMember()))).toList();
        var reports = polls.stream().map(poll -> saveReport(poll.getId())).toList();

        var outcomes = ConcurrentRace.run(tasks(reports));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes.stream().map(Outcome::value)).allMatch(result -> result.status() == 204);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_action", Long.class)).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report WHERE status = 'DISMISSED'", Long.class))
            .isEqualTo(4L);
    }

    private void assertExactlyOneSucceeded(List<Outcome<HttpOutcome>> outcomes, int expectedCount) {
        var results = outcomes.stream().map(Outcome::value).toList();
        assertThat(outcomes).hasSize(expectedCount).allMatch(Outcome::succeeded);
        assertThat(results).noneMatch(result -> result.status() >= 500);
        assertThat(results).filteredOn(result -> result.status() == 204).hasSize(1);
        assertThat(results).filteredOn(result -> result.status() == 409).hasSize(expectedCount - 1)
            .allMatch(result -> result.body().contains("REPORT_ALREADY_HANDLED"));
    }

    /** 승자 한 번의 처리 결과만 남았는지 본다: 투표는 그대로, 신고 DISMISSED, 이력 DISMISS_REPORT 1건. */
    private void assertSingleHandling(Long pollId, List<Report> reports) {
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM poll WHERE id = ?", String.class, pollId))
            .isEqualTo("PUBLIC");
        for (var report : reports) {
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM report WHERE id = ?", String.class,
                report.getId())).isEqualTo("DISMISSED");
        }
        var actions = jdbcTemplate.queryForList("SELECT action_type, target_poll_id FROM admin_action");
        assertThat(actions).hasSize(1);
        assertThat(actions.getFirst().get("action_type")).isEqualTo("DISMISS_REPORT");
        assertThat(((Number) actions.getFirst().get("target_poll_id")).longValue()).isEqualTo(pollId);
    }

    private Report saveReport(Long pollId) {
        return reportJpaRepository.saveAndFlush(newReport(pollId));
    }

    /** 관리자 한 명이 같은 순간에 같은 기각을 여러 번 보낸다(더블클릭·재전송). */
    private List<Callable<HttpOutcome>> tasks(List<Report> targets) {
        var cookie = adminCookie(saveMember(Role.ADMIN));

        return targets.stream()
            .map(report -> (Callable<HttpOutcome>) () -> postDismiss(report, cookie))
            .toList();
    }

    private Cookie adminCookie(Long adminId) {
        return new Cookie(AuthCookies.ACCESS_COOKIE,
            jwtTokenProvider.createAccessToken(adminId, Role.ADMIN, LocalDateTime.now(TimeConfig.KST)));
    }

    private record HttpOutcome(int status, String body) {

    }

    private HttpOutcome postDismiss(Report report, Cookie cookie) throws Exception {
        var response = mvcTester.post()
            .uri("/api/v1/admin/reports/" + report.getId() + "/dismiss")
            .header("Origin", ALLOWED_ORIGIN)
            .cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY)
            .exchange()
            .getResponse();

        return new HttpOutcome(response.getStatus(), response.getContentAsString());
    }

}
