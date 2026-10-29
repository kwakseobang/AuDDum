package org.kwakmunsu.auddum.member.domain;

/** 회원 상태. 전이 규칙은 {@link Member}가 지킨다(도메인 모델의 회원 애그리거트). */
public enum MemberStatus {

    ACTIVE,
    SUSPENDED,
    WITHDRAWN

}
