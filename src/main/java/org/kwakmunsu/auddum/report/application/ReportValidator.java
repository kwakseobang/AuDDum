package org.kwakmunsu.auddum.report.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.domain.ReportErrorCode;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.kwakmunsu.auddum.report.infrastructure.ReportJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.springframework.stereotype.Component;

/** 신고 규칙 검증. 서비스가 시키면 규칙을 확인하고 어기면 도메인 오류를 던진다. */
@RequiredArgsConstructor
@Component
public class ReportValidator {

    private final ReportJpaRepository reportRepository;

    /** 이미 신고한 사람이면 409(REPORT_ALREADY_SUBMITTED)다. */
    public void validateNotReported(Poll poll, Reporter reporter) {
        if (reportRepository.existsByPollIdAndReporter(poll.getId(), reporter)) {
            throw new AuddumException(ReportErrorCode.REPORT_ALREADY_SUBMITTED);
        }
    }

}
