package org.kwakmunsu.auddum.poll.application.dto;

import java.time.LocalDateTime;
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

    /**
     * 투표와 참여 정보로 상세를 만든다. 마감 여부와 결과 공개 여부는 받은 시각 하나로 판단하고(규칙은 {@link Poll}이 가진다),
     * 결과를 공개하지 않으면 result는 null이다. 전체 참여 수는 결과 공개와 무관하게 담는다.
     */
    public static PollDetail of(Poll poll, Participation participation, LocalDateTime now) {
        boolean resultOpen = poll.isResultOpen(participation.participated(), now);

        return new PollDetail(
            poll,
            poll.isClosed(now),
            participation.tally().total(),
            participation.myChoice(),
            resultOpen ? participation.tally() : null
        );
    }

}
