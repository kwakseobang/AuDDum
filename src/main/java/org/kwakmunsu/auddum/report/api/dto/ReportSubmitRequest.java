package org.kwakmunsu.auddum.report.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.kwakmunsu.auddum.report.domain.ReportReason;
import org.kwakmunsu.auddum.report.domain.ReportReasonCode;

/**
 * 신고 요청. 여기서는 사유가 있는지(필수)만 확인하고, 사유별 상세 규칙(ETC 필수, 길이)은 도메인(ReportReason)이 검증한다.
 * 허용되지 않은 사유 값은 본문 형식 오류(MALFORMED_REQUEST)다.
 */
@Schema(description = "투표 신고 요청")
public record ReportSubmitRequest(
    @Schema(description = "신고 사유", example = "SPAM")
    @NotNull
    ReportReasonCode reason,

    @Schema(description = "상세 내용. 사유가 ETC일 때 필수이고 200자 이하(임시)", example = "특정인을 지목하는 내용입니다.", nullable = true)
    String detail
) {

    public ReportReason toReason() {
        return new ReportReason(reason, detail);
    }

}
