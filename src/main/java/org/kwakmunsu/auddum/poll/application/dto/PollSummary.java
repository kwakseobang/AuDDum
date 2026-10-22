package org.kwakmunsu.auddum.poll.application.dto;

import org.kwakmunsu.auddum.poll.domain.Poll;

/**
 * 목록의 투표 한 건.
 *
 * @param participantCount 전체 참여 수
 */
public record PollSummary(
    Poll poll,
    long participantCount
) {

}
