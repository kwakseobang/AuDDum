package org.kwakmunsu.auddum.poll.domain;

/** 투표 공개 상태. 전이 규칙은 {@link Poll}의 메서드가 지킨다. */
public enum PollStatus {

    PUBLIC,
    HIDDEN,
    DELETED

}
