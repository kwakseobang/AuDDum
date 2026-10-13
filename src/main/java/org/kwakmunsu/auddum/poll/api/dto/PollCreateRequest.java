package org.kwakmunsu.auddum.poll.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.kwakmunsu.auddum.poll.application.dto.NewPoll;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.support.config.TimeConfig;

/**
 * 투표 생성 요청. 문자열 길이는 유니코드 문자 단위로 세므로(앞뒤 공백 제거 후) 길이와 줄바꿈, 선택지 중복은 도메인이 검증한다.
 * 여기서는 형태(필수, 선택지 개수, 마감이 미래인지)만 확인한다.
 */
@Schema(description = "투표 생성 요청")
public record PollCreateRequest(
    @Schema(description = "질문. 앞뒤 공백 제거 후 1~500자, 줄바꿈 불가", example = "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?")
    @NotBlank
    String question,

    @Schema(description = "카테고리", example = "SOME")
    @NotNull
    Category category,

    @Schema(description = "선택지 2~5개. 각 앞뒤 공백 제거 후 1~50자, 서로 달라야 한다", example = "[\"관심 없음\", \"그냥 바쁜 것\", \"더 지켜봐야 함\"]")
    @NotNull
    @Size(min = 2, max = 5)
    List<@NotBlank String> options,

    @Schema(description = "마감 시각(선택). 현재보다 미래여야 하고, 없으면 마감 없는 투표", example = "2026-10-19T23:59:00+09:00", nullable = true)
    @Future
    OffsetDateTime closesAt
) {

    public NewPoll toNewPoll() {
        return NewPoll.builder()
            .question(question)
            .category(category)
            .options(options)
            .closesAt(closesAt == null ? null : closesAt.atZoneSameInstant(TimeConfig.KST).toLocalDateTime())
            .build();
    }

}
