package org.kwakmunsu.auddum.report.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.domain.ReportErrorCode;
import org.kwakmunsu.auddum.report.domain.ReportStatus;
import org.kwakmunsu.auddum.report.infrastructure.PollReportCount;
import org.kwakmunsu.auddum.report.infrastructure.ReportJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/**
 * 신고 읽기. 다른 도메인(admin)이 서비스가 아니라 이 클래스로 신고를 읽는다.
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여한다.
 */
@RequiredArgsConstructor
@Component
public class ReportReader {

    /** 상세에 담는 같은 투표의 다른 신고 상한. 익명 신고가 쌓여도 응답과 쿼리 크기가 일정하도록 한다. 전체 건수는 목록의 pollReportCount로 안다. */
    public static final int OTHER_REPORTS_LIMIT = 20;

    private final ReportJpaRepository reportRepository;

    /** 신고 한 건. 없으면 404(REPORT_NOT_FOUND)다. */
    public Report get(Long reportId) {
        return reportRepository.findById(reportId)
            .orElseThrow(() -> new AuddumException(ReportErrorCode.REPORT_NOT_FOUND));
    }

    /** 같은 상태의 신고를 접수 순으로 size개 돌려준다. cursorId(마지막으로 본 신고 id)가 없으면 첫 페이지다. */
    public Slice<Report> findByStatusAfter(ReportStatus status, Long cursorId, int size) {
        long afterId = cursorId == null ? 0L : cursorId;

        return reportRepository.findByStatusAndIdGreaterThanOrderByIdAsc(status, afterId, PageRequest.ofSize(size));
    }

    /** 같은 투표의 다른 신고를 접수 순으로 처음 {@value #OTHER_REPORTS_LIMIT}건까지 돌려준다(처리 상태와 무관). */
    public List<Report> findOthersOfSamePoll(Report report) {
        return reportRepository.findByPollIdAndIdNotOrderByIdAsc(
            report.getPollId(),
            report.getId(),
            PageRequest.ofSize(OTHER_REPORTS_LIMIT)
        );
    }

    /** 투표별 신고 수를 처리 상태와 무관하게 센다. 요청한 투표 ID가 모두 키이고, 신고가 없으면 0이다. */
    public Map<Long, Long> countByPollIds(Collection<Long> pollIds) {
        if (pollIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> counts = reportRepository.countByPollIds(pollIds).stream()
            .collect(Collectors.toMap(PollReportCount::pollId, PollReportCount::count));

        return pollIds.stream()
            .distinct()
            .collect(Collectors.toMap(Function.identity(), id -> counts.getOrDefault(id, 0L)));
    }

}
