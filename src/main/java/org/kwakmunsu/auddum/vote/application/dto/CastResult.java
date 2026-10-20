package org.kwakmunsu.auddum.vote.application.dto;

/**
 * 참여 결과.
 *
 * @param myChoice       내가 고른 선택지 순번
 * @param result         현재 결과
 * @param newCookieValue 새로 발급한 익명 쿠키 값. 발급하지 않았으면 null
 */
public record CastResult(int myChoice, PollTally result, String newCookieValue) {

}
