package org.kwakmunsu.auddum.member.domain;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.test.util.ReflectionTestUtils;

/*
 * 테스트 코드에서 반복적으로 사용되는 Member 관련 데이터를 모아둔 클래스
 * */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemberFixture {

    public static Member createMember() {
        return Member.signUp();
    }

    public static Member createMember(Role role) {
        var member = createMember();
        ReflectionTestUtils.setField(member, "role", role);

        return member;
    }

    public static Member createWithdrawnAdmin() {
        var member = createMember(Role.ADMIN);
        member.withdraw(LocalDateTime.now(TimeConfig.KST));

        return member;
    }

    /** 상태 전이 메서드가 없는 상태(정지)를 테스트에서 만들 때 쓴다. */
    public static Member changeStatus(Member member, MemberStatus status) {
        ReflectionTestUtils.setField(member, "status", status);

        return member;
    }

}
