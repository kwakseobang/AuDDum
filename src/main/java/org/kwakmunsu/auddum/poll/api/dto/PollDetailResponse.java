package org.kwakmunsu.auddum.poll.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.support.config.TimeConfig;

/** 투표 상세. 참여 정보가 필요한 myChoice·result는 참여 기능(#15·#16) 전까지 항상 null이다. */
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

    @Schema(description = "참여 수. 참여 기능(#15) 전까지 0", example = "0")
    long participantCount,

    @Schema(description = "내가 고른 선택지 position. 참여 기능 전까지 null", nullable = true)
    Integer myChoice,

    @Schema(description = "결과. 참여 기능 전까지 null", nullable = true)
    Object result
) {

    public static PollDetailResponse of(Poll poll, LocalDateTime now) {
        List<OptionResponse> options = poll.getOptions().stream()
            .map(option -> new OptionResponse(option.getPosition().value(), option.getContent().value()))
            .toList();

        return new PollDetailResponse(
            poll.getPublicId().value(),
            poll.getQuestion().value(),
            poll.getCategory(),
            poll.getStatus(),
            options,
            TimeConfig.toKstOffset(poll.getClosesAt() == null ? null : poll.getClosesAt().value()),
            poll.isClosed(now),
            0,
            null,
            null
        );
    }

    public record OptionResponse(int position, String content) {

    }

}
