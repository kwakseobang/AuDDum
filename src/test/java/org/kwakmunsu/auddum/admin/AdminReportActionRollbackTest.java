package org.kwakmunsu.auddum.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.report.ReportFixture.ALLOWED_ORIGIN;
import static org.kwakmunsu.auddum.report.ReportFixture.newPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newReport;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.MySqlConcurrencyTestSupport;
import org.kwakmunsu.auddum.admin.application.AdminActionWriter;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.infrastructure.ReportJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 신고 조치 중간에 이력 기록이 실패하면 대상 상태 변경과 신고 종료가 전부 롤백되는지 확인한다.
 * 기준은 R-ACTION-03 "신고 상태 변경과 이력 기록은 한 트랜잭션이고 실패하면 전부 롤백한다"(dismiss),
 * "대상 상태 변경, 신고 상태 변경, 이력 기록은 한 트랜잭션으로 처리한다. 실패하면 전부 롤백한다", 이슈 완료 기준 "조치 중 실패하면 전부 롤백된다".
 * 롤백은 @Transactional 테스트로 관찰할 수 없어 실제로 커밋하는 MySQL 통합 테스트로 두고, 이력 기록기를 spy로 감싸 예외를 던진다.
 * 상태는 JdbcTemplate으로 읽는다. 예외가 서블릿 밖으로 전파되든 500으로 변환되든 롤백만 본다(응답 코드는 docs가 정하지 않았다).
 */
@AutoConfigureMockMvc
@RequiredArgsConstructor
class AdminReportActionRollbackTest extends MySqlConcurrencyTestSupport {

    static final String BODY = "{\"reason\": \"롤백 확인\"}";

    final MockMvcTester mvcTester;
    final JdbcTemplate jdbcTemplate;
    final JwtTokenProvider jwtTokenProvider;
    final ReportJpaRepository reportJpaRepository;

    @MockitoSpyBean
    AdminActionWriter adminActionWriter;

    @Test
    void 투표_숨김_중_이력_기록이_실패하면_투표_상태와_신고_종료가_모두_원래대로_돌아온다() {
        var author = saveMember();
        var poll = savePoll(newPoll(author));
        var report = reportJpaRepository.saveAndFlush(newReport(poll.getId()));
        var other = reportJpaRepository.saveAndFlush(newReport(poll.getId()));
        var admin = saveMember(Role.ADMIN);
        doThrow(new IllegalStateException("이력 기록 실패")).when(adminActionWriter).record(any());

        post("polls/" + poll.getPublicId().value() + "/status", "HIDDEN", admin);

        verify(adminActionWriter, atLeastOnce()).record(any());
        assertNothingChanged(poll.getId(), author, List.of(report, other));
    }

    @Test
    void 투표_삭제_중_이력_기록이_실패하면_투표_상태와_신고_종료가_모두_원래대로_돌아온다() {
        var author = saveMember();
        var poll = savePoll(newPoll(author));
        var report = reportJpaRepository.saveAndFlush(newReport(poll.getId()));
        var other = reportJpaRepository.saveAndFlush(newReport(poll.getId()));
        var admin = saveMember(Role.ADMIN);
        doThrow(new IllegalStateException("이력 기록 실패")).when(adminActionWriter).record(any());

        post("polls/" + poll.getPublicId().value() + "/status", "DELETED", admin);

        verify(adminActionWriter, atLeastOnce()).record(any());
        assertNothingChanged(poll.getId(), author, List.of(report, other));
    }

    @Test
    void 신고_기각_중_이력_기록이_실패하면_신고와_같은_투표의_다른_신고가_종료되지_않는다() {
        var author = saveMember();
        var poll = savePoll(newPoll(author));
        var report = reportJpaRepository.saveAndFlush(newReport(poll.getId()));
        var other = reportJpaRepository.saveAndFlush(newReport(poll.getId()));
        var admin = saveMember(Role.ADMIN);
        doThrow(new IllegalStateException("이력 기록 실패")).when(adminActionWriter).record(any());

        post("reports/" + report.getId() + "/dismiss", admin);

        verify(adminActionWriter, atLeastOnce()).record(any());
        assertNothingChanged(poll.getId(), author, List.of(report, other));
    }

    @Test
    void 회원_정지_중_이력_기록이_실패하면_회원_상태와_정지_일시가_원래대로_돌아온다() {
        var author = saveMember();
        var poll = savePoll(newPoll(author));
        var report = reportJpaRepository.saveAndFlush(newReport(poll.getId()));
        var admin = saveMember(Role.ADMIN);
        doThrow(new IllegalStateException("이력 기록 실패")).when(adminActionWriter).record(any());

        post("members/" + author + "/suspend", admin);

        verify(adminActionWriter, atLeastOnce()).record(any());
        assertNothingChanged(poll.getId(), author, List.of(report));
    }

    private void assertNothingChanged(Long pollId, Long authorId, List<Report> reports) {
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM poll WHERE id = ?", String.class, pollId))
            .isEqualTo("PUBLIC");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM member WHERE id = ?", String.class, authorId))
            .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT suspended_at FROM member WHERE id = ?", Object.class, authorId))
            .isNull();
        for (var report : reports) {
            var row = jdbcTemplate.queryForMap("SELECT status, handled_at, handled_by FROM report WHERE id = ?",
                report.getId());
            assertThat(row.get("status")).isEqualTo("PENDING");
            assertThat(row.get("handled_at")).isNull();
            assertThat(row.get("handled_by")).isNull();
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_action", Long.class)).isZero();
    }

    private void post(String path, Long adminId) {
        post(path, BODY, adminId, false);
    }

    /** 투표 숨김·삭제는 같은 PATCH API라 본문에 조치 상태를 붙인다. */
    private void post(String path, String status, Long adminId) {
        post(path, BODY.replaceFirst("\\{", "{\"status\": \"" + status + "\", "), adminId, true);
    }

    private void post(String path, String content, Long adminId, boolean patch) {
        var cookie = new Cookie(AuthCookies.ACCESS_COOKIE,
            jwtTokenProvider.createAccessToken(adminId, Role.ADMIN, LocalDateTime.now(TimeConfig.KST)));

        try {
            (patch ? mvcTester.patch() : mvcTester.post()).uri("/api/v1/admin/" + path)
                .header("Origin", ALLOWED_ORIGIN)
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .exchange();
        } catch (RuntimeException expected) {
            // 예외 전파 방식은 검증 대상이 아니다.
        }
    }

}
