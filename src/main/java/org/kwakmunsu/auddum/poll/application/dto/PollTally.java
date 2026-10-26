package org.kwakmunsu.auddum.poll.application.dto;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;

/** 투표 결과. 선택지는 순번 오름차순이고 득표가 없는 선택지도 0으로 포함한다. */
@Builder(access = AccessLevel.PRIVATE)
public record PollTally(long total, List<OptionTally> options) {

    public static PollTally of(long total, List<OptionTally> options) {
        return PollTally.builder()
            .total(total)
            .options(options)
            .build();
    }

}
