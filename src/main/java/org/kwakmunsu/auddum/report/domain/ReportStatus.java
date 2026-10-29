package org.kwakmunsu.auddum.report.domain;

/** 신고 상태. 접수는 PENDING이고, 관리자가 처리하면 RESOLVED나 DISMISSED로 끝난다. */
public enum ReportStatus {

    PENDING,
    RESOLVED,
    DISMISSED

}
