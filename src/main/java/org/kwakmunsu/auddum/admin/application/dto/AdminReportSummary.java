package org.kwakmunsu.auddum.admin.application.dto;

import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.domain.Report;

/** 관리자 신고 목록의 한 건. 신고와 대상 투표, 그 투표에 쌓인 신고 수를 묶는다. */
public record AdminReportSummary(Report report, Poll poll, long pollReportCount) {

}
