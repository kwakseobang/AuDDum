package org.kwakmunsu.auddum.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.admin.application.dto.AdminReportSummary;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.domain.ReportReasonCode;
import org.kwakmunsu.auddum.report.domain.ReportStatus;
import org.kwakmunsu.auddum.support.config.TimeConfig;

@Builder(access = AccessLevel.PRIVATE)
@Schema(description = "관리자 신고 목록 항목")
public record AdminReportSummaryResponse(
    @Schema(description = "신고 내부 id", example = "17")
    long id,

    PollInfo poll,

    ReportReasonCode reason,

    ReportStatus status,

    @Schema(description = "접수 시각(KST 오프셋)", example = "2026-10-12T14:20:00+09:00")
    OffsetDateTime createdAt,

    @Schema(description = "같은 투표에 쌓인 신고 수(처리된 신고 포함)", example = "3")
    long pollReportCount
) {

    public static AdminReportSummaryResponse from(AdminReportSummary summary) {
        Report report = summary.report();

        return AdminReportSummaryResponse.builder()
            .id(report.getId())
            .poll(PollInfo.from(summary.poll()))
            .reason(report.getReason().code())
            .status(report.getStatus())
            .createdAt(TimeConfig.toKstOffset(report.getCreatedAt()))
            .pollReportCount(summary.pollReportCount())
            .build();
    }

    @Schema(description = "신고 대상 투표 요약")
    public record PollInfo(
        @Schema(description = "공개 ID", example = "aB3dE5fG7hJk")
        String id,

        String question,

        PollStatus status
    ) {

        static PollInfo from(Poll poll) {
            return new PollInfo(poll.getPublicId().value(), poll.getQuestion().value(), poll.getStatus());
        }

    }

}
