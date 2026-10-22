package org.kwakmunsu.auddum.poll.application.dto;

/**
 * 한 투표의 참여 정보. 결과 공개 여부는 담지 않고 사실만 담는다.
 *
 * @param myChoice 조회하는 사람이 고른 선택지 순번. 참여하지 않았거나 참여자를 알 수 없으면 null
 * @param tally    선택지별 득표와 전체 참여 수
 */
public record Participation(Integer myChoice, PollTally tally) {

    /** 조회하는 사람이 이 투표에 참여했는가. */
    public boolean participated() {
        return myChoice != null;
    }

}
