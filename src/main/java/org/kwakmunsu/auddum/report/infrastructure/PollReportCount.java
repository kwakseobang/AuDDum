package org.kwakmunsu.auddum.report.infrastructure;

/** 투표별 신고 수 집계 한 줄(JPQL 생성자 식으로 채운다). */
public record PollReportCount(Long pollId, Long count) {

}
