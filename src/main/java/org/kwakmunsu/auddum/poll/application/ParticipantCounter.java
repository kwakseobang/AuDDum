package org.kwakmunsu.auddum.poll.application;

import java.util.Collection;
import java.util.Map;

/**
 * 투표 목록이 필요로 하는 투표별 참여 수. poll이 필요한 것을 선언하고 참여 기록을 가진 vote가 구현한다(poll은 vote를 쓸 수 없다).
 */
public interface ParticipantCounter {

    /** 요청한 모든 투표 ID를 키로 가진 투표별 참여 수. 참여가 없는 투표는 0이고, 입력이 비어 있으면 빈 맵이다. */
    Map<Long, Long> countByPollIds(Collection<Long> pollIds);

}
