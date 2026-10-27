package org.kwakmunsu.auddum.admin.api.dto;

import org.kwakmunsu.auddum.report.domain.ReportStatus;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;

/**
 * 신고 목록 조회 파라미터(status, cursor, size 쿼리 파라미터를 묶는다).
 * status가 없으면 PENDING이고 정의되지 않은 값(빈 문자열 포함)은 MALFORMED_REQUEST다.
 * 커서는 불투명 문자열이고, size는 없으면 기본값, 범위를 벗어나면 1~50으로 보정한다.
 */
public record AdminReportListRequest(
    String status,
    String cursor,
    Integer size
) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 50;

    public ReportStatus reportStatus() {
        if (status == null) {
            return ReportStatus.PENDING;
        }

        try {
            return ReportStatus.valueOf(status);
        } catch (IllegalArgumentException _) {
            throw new AuddumException(DefaultErrorCode.MALFORMED_REQUEST);
        }
    }

    /** 커서가 없으면 null이고 첫 페이지다. 형식이 틀리면 MALFORMED_REQUEST다. */
    public Long cursorId() {
        ReportCursor decoded = ReportCursor.from(cursor);

        return decoded == null ? null : decoded.id();
    }

    public int pageSize() {
        return size == null ? DEFAULT_SIZE : Math.clamp(size, MIN_SIZE, MAX_SIZE);
    }

    public static String nextCursor(Long lastId) {
        return new ReportCursor(lastId).encode();
    }

}
