package org.kwakmunsu.auddum.poll.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollOption;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.poll.application.dto.PollDetail;

/** 투표 상세. 참여 정보(participantCount, myChoice, result)를 포함한다. */
@Builder(access = AccessLevel.PRIVATE)
@Schema(description = "투표 상세")
public record PollDetailResponse(
    @Schema(description = "공개 ID", example = "aB3dE5fG7hJk")
    String id,

    String question,

    Category category,

    @Schema(description = "노출 상태. 숨김·삭제는 작성자와 관리자에게만 내려간다")
    PollStatus status,

    List<OptionResponse> options,

    @Schema(description = "마감 시각(KST 오프셋). 마감 없으면 null", nullable = true, example = "2026-10-19T23:59:00+09:00")
    OffsetDateTime closesAt,

    boolean closed,

    @Schema(description = "전체 참여 수. 결과를 공개하지 않아도 내려간다", example = "42")
    long participantCount,

    @Schema(description = "내가 고른 선택지 position. 참여하지 않았으면 null", nullable = true)
    Integer myChoice,

    @Schema(description = "결과. 마감되었거나 내가 참여했을 때만 내려가고 아니면 null", nullable = true)
    PollResultResponse result
) {

    public static PollDetailResponse from(PollDetail detail) {
        Poll poll = detail.poll();
        List<OptionResponse> options = poll.getOptions().stream()
            .map(OptionResponse::from)
            .toList();

        return PollDetailResponse.builder()
            .id(poll.getPublicId().value())
            .question(poll.getQuestion().value())
            .category(poll.getCategory())
            .status(poll.getStatus())
            .options(options)
            .closesAt(TimeConfig.toKstOffset(poll.getClosesAt() == null ? null : poll.getClosesAt().value()))
            .closed(detail.closed())
            .participantCount(detail.participantCount())
            .myChoice(detail.myChoice())
            .result(detail.result() == null ? null : PollResultResponse.from(detail.result()))
            .build();
    }

    @Builder(access = AccessLevel.PRIVATE)
    public record OptionResponse(int position, String content) {

        static OptionResponse from(PollOption option) {
            return OptionResponse.builder()
                .position(option.getPosition().value())
                .content(option.getContent().value())
                .build();
        }

    }

}
