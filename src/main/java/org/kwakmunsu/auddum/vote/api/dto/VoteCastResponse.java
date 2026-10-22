package org.kwakmunsu.auddum.vote.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.poll.api.dto.PollResultResponse;
import org.kwakmunsu.auddum.vote.application.dto.CastResult;

/** 투표 참여 응답. */
@Builder(access = AccessLevel.PRIVATE)
@Schema(description = "투표 참여 응답")
public record VoteCastResponse(
    @Schema(description = "내가 고른 선택지 순번", example = "2")
    int myChoice,

    @Schema(description = "현재 결과")
    PollResultResponse result
) {

    public static VoteCastResponse from(CastResult cast) {
        return VoteCastResponse.builder()
            .myChoice(cast.myChoice())
            .result(PollResultResponse.from(cast.result()))
            .build();
    }

}
