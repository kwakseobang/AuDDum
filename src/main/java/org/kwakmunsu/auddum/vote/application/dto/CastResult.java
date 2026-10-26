package org.kwakmunsu.auddum.vote.application.dto;

import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.poll.application.dto.PollTally;

/**
 * 참여 결과.
 *
 * @param myChoice       내가 고른 선택지 순번
 * @param result         현재 결과
 * @param newCookieValue 새로 발급한 익명 쿠키 값. 발급하지 않았으면 null
 */
@Builder(access = AccessLevel.PRIVATE)
public record CastResult(int myChoice, PollTally result, String newCookieValue) {

    public static CastResult of(int myChoice, PollTally result, String newCookieValue) {
        return CastResult.builder()
            .myChoice(myChoice)
            .result(result)
            .newCookieValue(newCookieValue)
            .build();
    }

}
