package org.kwakmunsu.auddum.poll.application.dto;

import lombok.AccessLevel;
import lombok.Builder;

/** 선택지 하나의 득표 수. */
@Builder(access = AccessLevel.PRIVATE)
public record OptionTally(int position, long count) {

    public static OptionTally of(int position, long count) {
        return OptionTally.builder()
            .position(position)
            .count(count)
            .build();
    }

}
