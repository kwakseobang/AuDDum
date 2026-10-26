package org.kwakmunsu.auddum.poll.application.dto;

import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.poll.domain.Poll;

/**
 * 목록의 투표 한 건.
 *
 * @param participantCount 전체 참여 수
 */
@Builder(access = AccessLevel.PRIVATE)
public record PollSummary(
    Poll poll,
    long participantCount
) {

    public static PollSummary of(Poll poll, long participantCount) {
        return PollSummary.builder()
            .poll(poll)
            .participantCount(participantCount)
            .build();
    }

}
