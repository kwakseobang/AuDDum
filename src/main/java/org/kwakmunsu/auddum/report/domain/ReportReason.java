package org.kwakmunsu.auddum.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.kwakmunsu.auddum.support.error.AuddumException;

/**
 * 신고 사유와 상세. 상세는 {@code ETC}일 때 필수(공백뿐이면 없는 것)이고, 다른 사유에 담겨 와도 버리지 않고 그대로 가진다.
 * 길이는 유니코드 문자 단위로 센다.
 */
@Embeddable
public record ReportReason(
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    ReportReasonCode code,

    @Column(name = "detail", length = 200)
    String detail
) {

    /** 상세 글자 수 상한. 임시 값이다(확정 전 항목, R-REPORT-03). ERD의 detail 컬럼 길이와 맞춘다. */
    public static final int DETAIL_MAX_LENGTH = 200;

    public ReportReason {
        if (code == null) {
            throw AuddumException.validation("reason", "신고 사유는 필수입니다.");
        }
        if (code == ReportReasonCode.ETC && (detail == null || detail.isBlank())) {
            throw AuddumException.validation("detail", "기타 사유는 상세 내용이 필요합니다.");
        }
        if (detail != null && detail.codePointCount(0, detail.length()) > DETAIL_MAX_LENGTH) {
            throw AuddumException.validation("detail", "상세 내용은 " + DETAIL_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

}
