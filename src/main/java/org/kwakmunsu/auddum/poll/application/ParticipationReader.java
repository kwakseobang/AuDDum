package org.kwakmunsu.auddum.poll.application;

import java.util.Collection;
import java.util.Map;
import org.kwakmunsu.auddum.poll.application.dto.Participation;
import org.kwakmunsu.auddum.poll.application.dto.Viewer;
import org.kwakmunsu.auddum.poll.domain.Poll;

/**
 * 투표 목록·상세가 필요로 하는 참여 정보. poll이 필요한 것을 선언하고 참여 기록을 가진 vote가 구현한다(poll은 vote를 쓸 수 없다).
 * 결과를 공개할지는 poll이 정하고, 구현은 사실(참여 수, 내 선택, 득표)만 돌려준다.
 */
public interface ParticipationReader {

    /** 요청한 모든 투표 ID를 키로 가진 투표별 참여 수. 참여가 없는 투표는 0이고, 입력이 비어 있으면 빈 맵이다. */
    Map<Long, Long> countByPollIds(Collection<Long> pollIds);

    /**
     * 이 투표의 선택지별 득표와, 조회하는 사람의 선택을 돌려준다. 조회하는 사람이 참여자인지 알 수 없으면(쿠키 없음·위조) 선택은 비어 있다.
     * 읽기만 하고 쿠키를 발급하지 않으며, 회원 상태는 확인하지 않는다.
     */
    Participation readFor(Poll poll, Viewer viewer);

}
