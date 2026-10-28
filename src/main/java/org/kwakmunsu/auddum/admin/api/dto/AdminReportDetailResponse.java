package org.kwakmunsu.auddum.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.admin.application.dto.AdminReportDetail;
import org.kwakmunsu.auddum.admin.domain.AdminAction;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.domain.ReportReasonCode;
import org.kwakmunsu.auddum.report.domain.ReportStatus;
import org.kwakmunsu.auddum.support.config.TimeConfig;

/** 관리자 신고 상세. 신고자는 노출하지 않는다. */
@Builder(access = AccessLevel.PRIVATE)
@Schema(description = "관리자 신고 상세")
public record AdminReportDetailResponse(
    long id,

    ReportReasonCode reason,

    @Schema(description = "신고 상세 설명", nullable = true)
    String detail,

    ReportStatus status,

    @Schema(description = "접수 시각(KST 오프셋)", example = "2026-10-12T14:20:00+09:00")
    OffsetDateTime createdAt,

    @Schema(description = "처리 시각. 처리 전에는 null", nullable = true)
    OffsetDateTime handledAt,

    @Schema(description = "처리한 관리자 회원 id. 처리 전에는 null", nullable = true)
    Long handledBy,

    PollInfo poll,

    @Schema(description = "같은 투표의 이 신고를 뺀 신고(접수 순)")
    List<OtherReport> otherReports,

    @Schema(description = "이 신고와 연결된 조치 이력(시각 순)")
    List<ActionHistory> actions
) {

    public static AdminReportDetailResponse from(AdminReportDetail detail) {
        Report report = detail.report();

        return AdminReportDetailResponse.builder()
            .id(report.getId())
            .reason(report.getReason().code())
            .detail(report.getReason().detail())
            .status(report.getStatus())
            .createdAt(TimeConfig.toKstOffset(report.getCreatedAt()))
            .handledAt(TimeConfig.toKstOffset(report.getHandledAt()))
            .handledBy(report.getHandledBy())
            .poll(PollInfo.from(detail.poll()))
            .otherReports(detail.otherReports().stream().map(OtherReport::from).toList())
            .actions(detail.actions().stream().map(ActionHistory::from).toList())
            .build();
    }

    @Schema(description = "신고 대상 투표")
    public record PollInfo(
        @Schema(description = "공개 ID", example = "aB3dE5fG7hJk")
        String id,

        String question,

        PollStatus status,

        @Schema(description = "작성자 회원 내부 id")
        Long authorId
    ) {

        static PollInfo from(Poll poll) {
            return new PollInfo(poll.getPublicId().value(), poll.getQuestion().value(), poll.getStatus(), poll.getAuthorId());
        }

    }

    @Schema(description = "같은 투표의 다른 신고")
    public record OtherReport(
        long id,

        ReportReasonCode reason,

        @Schema(nullable = true)
        String detail,

        ReportStatus status,

        OffsetDateTime createdAt
    ) {

        static OtherReport from(Report report) {
            return new OtherReport(
                report.getId(),
                report.getReason().code(),
                report.getReason().detail(),
                report.getStatus(),
                TimeConfig.toKstOffset(report.getCreatedAt())
            );
        }

    }

    @Schema(description = "조치 이력 한 건")
    public record ActionHistory(
        long id,

        String actionType,

        long adminId,

        String reason,

        OffsetDateTime createdAt
    ) {

        static ActionHistory from(AdminAction action) {
            return new ActionHistory(
                action.getId(),
                action.getType().name(),
                action.getAdminId(),
                action.getReason(),
                TimeConfig.toKstOffset(action.getCreatedAt())
            );
        }

    }

}
