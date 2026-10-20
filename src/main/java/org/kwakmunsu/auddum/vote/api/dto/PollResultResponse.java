package org.kwakmunsu.auddum.vote.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.kwakmunsu.auddum.vote.application.dto.PollTally;

/** 투표 결과. 득표율은 내리지 않고 득표 수만 내린다. 참여 응답과 투표 상세가 같은 모양을 쓴다. */
@Schema(description = "투표 결과")
public record PollResultResponse(
    @Schema(description = "전체 참여 수", example = "42")
    long total,

    @Schema(description = "선택지별 득표 수. 순번 오름차순, 득표가 없는 선택지도 포함")
    List<Option> options
) {

    public static PollResultResponse from(PollTally tally) {
        return new PollResultResponse(
            tally.total(),
            tally.options().stream().map(option -> new Option(option.position(), option.count())).toList()
        );
    }

    @Schema(description = "선택지 하나의 득표 수")
    public record Option(
        @Schema(description = "선택지 순번", example = "1")
        int position,

        @Schema(description = "득표 수", example = "10")
        long count
    ) {

    }

}
