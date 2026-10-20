package org.kwakmunsu.auddum.vote.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 투표 참여 요청. 순번이 이 투표의 선택지인지는 도메인이 판단한다(처리 순서상 기존 참여와 마감 뒤). */
@Schema(description = "투표 참여 요청")
public record VoteCastRequest(
    @Schema(description = "선택지 순번(1부터)", example = "2")
    @NotNull
    Integer position
) {

}
