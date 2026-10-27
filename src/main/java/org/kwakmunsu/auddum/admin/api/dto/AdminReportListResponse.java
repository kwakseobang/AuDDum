package org.kwakmunsu.auddum.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.admin.application.dto.AdminReportSummary;
import org.springframework.data.domain.Slice;

@Builder(access = AccessLevel.PRIVATE)
@Schema(description = "관리자 신고 목록 한 페이지")
public record AdminReportListResponse(
    List<AdminReportSummaryResponse> items,

    @Schema(description = "다음 페이지 커서. 마지막이면 null", nullable = true, example = "eyJpZCI6MTd9")
    String nextCursor
) {

    public static AdminReportListResponse of(Slice<AdminReportSummary> slice) {
        List<AdminReportSummaryResponse> items = slice.getContent().stream()
            .map(AdminReportSummaryResponse::from)
            .toList();

        String nextCursor = slice.hasNext()
            ? AdminReportListRequest.nextCursor(slice.getContent().getLast().report().getId())
            : null;

        return AdminReportListResponse.builder()
            .items(items)
            .nextCursor(nextCursor)
            .build();
    }

}
