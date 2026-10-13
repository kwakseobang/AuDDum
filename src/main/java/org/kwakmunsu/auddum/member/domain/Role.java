package org.kwakmunsu.auddum.member.domain;

/** 회원 권한. API로 바꿀 수 없고 관리자는 DB에서 직접 부여한다. */
public enum Role {

    MEMBER,
    ADMIN

}
