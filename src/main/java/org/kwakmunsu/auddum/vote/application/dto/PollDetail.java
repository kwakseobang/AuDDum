package org.kwakmunsu.auddum.vote.application.dto;

import org.kwakmunsu.auddum.poll.domain.Poll;

/**
 * 투표 상세에 참여 정보를 더한 조회 결과.
 *
 * @param closed           요청 시각 기준 마감 여부(한 번만 판단한 값)
 * @param participantCount 전체 참여 수. 결과를 공개하지 않아도 내려준다
 * @param myChoice         내가 고른 선택지 순번. 참여하지 않았으면 null
 * @param result           결과. 공개하지 않는 경우 null
 */
public record PollDetail(
    Poll poll,
    boolean closed,
    long participantCount,
    Integer myChoice,
    PollTally result
) {

}
