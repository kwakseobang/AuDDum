package org.kwakmunsu.auddum.admin.application.dto;

import java.util.List;
import org.kwakmunsu.auddum.admin.domain.AdminAction;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.domain.Report;

/** 관리자 신고 상세. 신고, 대상 투표(숨김·삭제여도), 같은 투표의 다른 신고, 이 신고와 연결된 조치 이력을 묶는다. */
public record AdminReportDetail(Report report, Poll poll, List<Report> otherReports, List<AdminAction> actions) {

}
