package org.kwakmunsu.auddum.report.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.domain.ReportErrorCode;
import org.kwakmunsu.auddum.report.domain.ReportReason;
import org.kwakmunsu.auddum.report.domain.ReportStatus;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.kwakmunsu.auddum.report.infrastructure.ReportJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/** 신고 쓰기. 저장하고, 저장소가 던지는 유니크 제약 위반을 도메인 오류로 바꾼다. */
@Slf4j
@RequiredArgsConstructor
@Component
public class ReportWriter {

    private final ReportJpaRepository reportRepository;

    /** 신고를 접수해 저장한다. 저장에서 유니크 위반이 나면 {@link #saveOnce}가 409로 바꾼다. */
    public Report submit(Poll poll, Reporter reporter, ReportReason reason) {
        Report report = Report.submit(poll.getId(), reporter, reason);
        saveOnce(report);

        log.info("[ReportWriter] 신고 접수 - pollId={}, reason={}, memberId={}",
            report.getPollId(),
            reason.code(),
            reporter.memberId()
        );

        return report;
    }

    /**
     * 처리한 신고를 저장하고, 같은 투표의 다른 대기 신고도 같은 결과로 함께 종료한다.
     * 읽은 뒤 다른 요청이 먼저 처리해 버전이 어긋나면(동시에 들어온 더블클릭·재전송에서 진 쪽) 409 REPORT_ALREADY_HANDLED다.
     * 충돌은 반영 시점에 나므로 여기서 바로 flush해 이 호출에서 잡는다.
     */
    public void saveHandled(Report report) {
        try {
            reportRepository.saveAndFlush(report);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new AuddumException(ReportErrorCode.REPORT_ALREADY_HANDLED, e);
        }

        closePendingOfPoll(report.getPollId(), report.getStatus(), report.getHandledBy(), report.getHandledAt());
    }

    /** 투표를 숨기거나 삭제했을 때 그 투표의 대기 신고를 모두 조치 완료(RESOLVED)로 종료한다. */
    public void resolvePendingOfPoll(Long pollId, Long adminId, LocalDateTime now) {
        closePendingOfPoll(pollId, ReportStatus.RESOLVED, adminId, now);
    }

    /**
     * 같은 투표의 대기 신고를 모두 같은 결과로 종료한다. 조치한 신고 자신은 이미 처리 상태라 걸리지 않는다. 대량 갱신이라 영속성 컨텍스트의 신고 엔티티는 갱신되지 않으므로, 부른 쪽은 이 신고들을 다시 읽지 않는다.
     */
    private void closePendingOfPoll(Long pollId, ReportStatus result, Long adminId, LocalDateTime now) {
        int closed = reportRepository.closePendingByPollId(pollId, result, adminId, now);

        log.info("[ReportWriter] 같은 투표의 대기 신고 일괄 종료 - pollId={}, result={}, count={}", pollId, result, closed);
    }

    /**
     * 저장하고 바로 반영한다. 같은 신고자의 기록이 이미 들어와 유니크 제약을 어기면(동시 요청에서 진 쪽) 409 REPORT_ALREADY_SUBMITTED다.
     * 외래키·CHECK 같은 다른 제약 위반은 진짜 오류이므로 그대로 올린다.
     */
    public void saveOnce(Report report) {
        try {
            reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new AuddumException(ReportErrorCode.REPORT_ALREADY_SUBMITTED, e);
            }

            throw e;
        }
    }

    private boolean isUniqueViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException cause
            && cause.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
    }

}
