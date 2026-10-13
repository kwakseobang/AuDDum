package org.kwakmunsu.auddum.member.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/*
 * 테스트 코드에서 반복적으로 사용되는 Member 관련 데이터를 모아둔 클래스
 * */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemberFixture {

    public static Member createMember() {
        return Member.signUp();
    }

}
