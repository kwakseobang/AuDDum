package org.kwakmunsu.auddum.poll.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import org.kwakmunsu.auddum.poll.application.dto.PollSummary;
import org.springframework.data.domain.Slice;

@Schema(description = "투표 목록 한 페이지")
public record PollListResponse(
    List<PollSummaryResponse> items,

    @Schema(description = "다음 페이지 커서. 마지막이면 null", nullable = true, example = "eyJpZCI6MTIzfQ")
    String nextCursor
) {

    public static PollListResponse of(Slice<PollSummary> slice, LocalDateTime now) {
        List<PollSummaryResponse> items = slice.getContent().stream()
            .map(summary -> PollSummaryResponse.of(summary, now))
            .toList();

        // 다음 페이지 커서를 계산한다. 마지막 페이지면 null이다.
        String nextCursor = slice.hasNext() ? PollListRequest.nextCursor(slice.getContent().getLast().poll().getId()) : null;

        return new PollListResponse(items, nextCursor);
    }

}
