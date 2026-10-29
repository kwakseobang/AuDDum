package org.kwakmunsu.auddum.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.report.ReportFixture.handled;
import static org.kwakmunsu.auddum.report.ReportFixture.newReport;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.AuddumException;

/**
 * 신고 처리 행위. 기준은 R-REPORT-05, R-REPORT-06, R-REPORT-07, Report.dismiss, 신고 상태 전이, REPORT_ALREADY_HANDLED(409).
 */
class ReportHandleTest {

    private static final Long POLL_ID = 10L;
    private static final Long ADMIN_ID = 99L;
    private static final Long OTHER_ADMIN_ID = 100L;

    private final LocalDateTime now = LocalDateTime.now(TimeConfig.KST);

    @Test
    void PENDING_신고를_dismiss하면_DISMISSED이고_처리_일시와_처리자를_기록한다() {
        var report = newReport(POLL_ID);

        report.dismiss(ADMIN_ID, now);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(report.isPending()).isFalse();
        assertThat(report.getHandledAt()).isEqualTo(now);
        assertThat(report.getHandledBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void 이미_RESOLVED인_신고를_dismiss하면_409_REPORT_ALREADY_HANDLED이고_상태는_그대로다() {
        var report = handled(newReport(POLL_ID), ReportStatus.RESOLVED, ADMIN_ID);
        var handledAt = report.getHandledAt();

        assertThatThrownBy(() -> report.dismiss(OTHER_ADMIN_ID, now.plusMinutes(1)))
            .isInstanceOfSatisfying(AuddumException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_ALREADY_HANDLED));
        assertThat(ReportErrorCode.REPORT_ALREADY_HANDLED.getStatus().value()).isEqualTo(409);
        assertThat(report.getHandledAt()).isEqualTo(handledAt);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getHandledBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void 이미_DISMISSED인_신고를_dismiss하면_409_REPORT_ALREADY_HANDLED다() {
        var report = handled(newReport(POLL_ID), ReportStatus.DISMISSED, ADMIN_ID);

        assertThatThrownBy(() -> report.dismiss(OTHER_ADMIN_ID, now))
            .isInstanceOfSatisfying(AuddumException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_ALREADY_HANDLED));
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
    }

    @Test
    void 처리하면_투표_ID와_신고_사유는_바뀌지_않는다() {
        var report = newReport(POLL_ID);
        var reason = report.getReason();

        report.dismiss(ADMIN_ID, now);

        assertThat(report.getPollId()).isEqualTo(POLL_ID);
        assertThat(report.getReason()).isEqualTo(reason);
    }

}
