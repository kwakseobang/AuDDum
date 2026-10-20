package org.kwakmunsu.auddum.vote.application.dto;

/**
 * 어느 투표의 몇 번 선택지인지. 형식(공개 ID)과 범위(순번)의 검증은 각각 투표 조회와 도메인이 한다.
 *
 * @param pollPublicId 투표 공개 ID
 * @param position     고른 선택지 순번
 */
public record PollChoice(String pollPublicId, int position) {

}
