package org.kwakmunsu.auddum.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 신고 접수 행위. 기준은 R-REPORT-01(도메인-모델 "submit ... 상태를 PENDING으로 둔다"), 신고 상태 절, API-명세.
 */
class ReportTest {

    private static final Long POLL_ID = 10L;

    @Test
    void 접수한_신고는_PENDING이고_처리_대기_상태다() {
        var report = Report.submit(POLL_ID, Reporter.member(7L), new ReportReason(ReportReasonCode.SPAM, null));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(report.isPending()).isTrue();
    }

    @Test
    void 접수한_신고는_투표_신고자_사유를_그대로_가진다() {
        var anonId = UUID.randomUUID();
        var reason = new ReportReason(ReportReasonCode.ETC, "특정인을 지목하는 내용입니다.");

        var report = Report.submit(POLL_ID, Reporter.anonymous(anonId), reason);

        assertThat(report.getPollId()).isEqualTo(POLL_ID);
        assertThat(report.getReporter()).isEqualTo(Reporter.anonymous(anonId));
        assertThat(report.getReason()).isEqualTo(reason);
    }

    @Test
    void 접수한_신고는_처리_일시와_처리_관리자가_비어_있다() {
        var report = Report.submit(POLL_ID, Reporter.member(7L), new ReportReason(ReportReasonCode.ABUSE_HATE, null));

        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getHandledBy()).isNull();
    }

    @Test
    void 신고_상태는_접수_처리_기각_세_가지다() {
        assertThat(ReportStatus.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("PENDING", "RESOLVED", "DISMISSED");
    }

}
