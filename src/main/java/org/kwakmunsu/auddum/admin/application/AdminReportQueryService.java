package org.kwakmunsu.auddum.admin.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.admin.application.dto.AdminReportDetail;
import org.kwakmunsu.auddum.admin.application.dto.AdminReportSummary;
import org.kwakmunsu.auddum.poll.application.PollReader;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.application.ReportReader;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.domain.ReportStatus;
import org.kwakmunsu.auddum.support.stereotype.QueryService;
import org.springframework.data.domain.Slice;

/** 관리자 신고 조회. 신고(report)와 대상 투표(poll)를 조합하고, 첫 단계에서 관리자 권한을 확인한다. */
@RequiredArgsConstructor
@QueryService
public class AdminReportQueryService {

    private final AdminAccessValidator adminAccessValidator;
    private final ReportReader reportReader;
    private final PollReader pollReader;
    private final AdminActionReader adminActionReader;

    /** 같은 상태의 신고를 접수 순으로 한 페이지 돌려준다. 대상 투표와 투표별 신고 수는 페이지 단위로 한 번에 읽는다. */
    public Slice<AdminReportSummary> getReports(Long adminId, ReportStatus status, Long cursorId, int size) {
        adminAccessValidator.validateAdmin(adminId);

        Slice<Report> reports = reportReader.findByStatusAfter(status, cursorId, size);

        List<Long> pollIds = reports.getContent().stream()
            .map(Report::getPollId)
            .distinct()
            .toList();

        Map<Long, Poll> polls = pollReader.findAllByIds(pollIds).stream()
            .collect(Collectors.toMap(Poll::getId, Function.identity()));
        Map<Long, Long> counts = reportReader.countByPollIds(pollIds);

        return reports.map(report -> new AdminReportSummary(
            report,
            polls.get(report.getPollId()),
            counts.get(report.getPollId())
        ));
    }

    /** 신고 한 건과 대상 투표(숨김·삭제여도), 같은 투표의 다른 신고를 돌려준다. 없는 신고는 404다. */
    public AdminReportDetail getReportDetail(Long adminId, Long reportId) {
        adminAccessValidator.validateAdmin(adminId);

        Report report = reportReader.find(reportId);
        Poll poll = pollReader.find(report.getPollId());

        return new AdminReportDetail(
            report,
            poll,
            reportReader.findOthersOfSamePoll(report),
            adminActionReader.findByReport(report)
        );
    }

}
