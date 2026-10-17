package org.kwakmunsu.auddum.member.domain;

/** 회원이 탈퇴했다. 탈퇴 트랜잭션 안에서 발행되며, 받는 쪽은 같은 트랜잭션에서 자기 데이터를 정리한다. */
public record MemberWithdrawnEvent(Long memberId) {

}
