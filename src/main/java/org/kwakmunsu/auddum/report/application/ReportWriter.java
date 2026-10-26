package org.kwakmunsu.auddum.report.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.domain.ReportErrorCode;
import org.kwakmunsu.auddum.report.domain.ReportReason;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.kwakmunsu.auddum.report.infrastructure.ReportJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.springframework.dao.DataIntegrityViolationException;
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
