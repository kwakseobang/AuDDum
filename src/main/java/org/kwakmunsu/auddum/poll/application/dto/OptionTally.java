package org.kwakmunsu.auddum.poll.application.dto;

/** 선택지 하나의 득표 수. */
public record OptionTally(int position, long count) {

}
