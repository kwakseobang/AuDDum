package org.kwakmunsu.auddum.admin.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.admin.domain.AdminAction;
import org.kwakmunsu.auddum.report.application.ReportReader;
import org.kwakmunsu.auddum.report.application.ReportWriter;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.CommandService;

/** 관리자의 신고 처리. 신고 상태 변경과 이력 기록을 한 트랜잭션으로 처리하고, 실패하면 전부 롤백한다. */
@RequiredArgsConstructor
@CommandService
public class AdminReportCommandService {

    private final AdminAccessValidator adminAccessValidator;
    private final ReportReader reportReader;
    private final ReportWriter reportWriter;
    private final AdminActionWriter adminActionWriter;

    /** 신고를 기각하고 같은 투표의 다른 대기 신고도 함께 기각한다. 투표와 회원은 바꾸지 않는다. 이미 처리된 신고는 409다. */
    public void dismiss(Long adminId, Long reportId, String reason) {
        adminAccessValidator.validateAdmin(adminId);

        Report report = reportReader.find(reportId);
        // 사유 검증(400)을 대상 상태 확인(409·404)보다 먼저 하려고 이력을 먼저 만든다.
        AdminAction history = AdminAction.dismissReport(adminId, report.getPollId(), reportId, reason);

        report.dismiss(adminId, LocalDateTime.now(TimeConfig.KST));

        reportWriter.saveHandled(report);
        adminActionWriter.record(history);
    }

}
