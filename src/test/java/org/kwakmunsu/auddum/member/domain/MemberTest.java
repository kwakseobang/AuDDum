package org.kwakmunsu.auddum.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void 가입하면_상태는_ACTIVE이고_권한은_MEMBER다() {
        var member = Member.signUp();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getRole()).isEqualTo(Role.MEMBER);
        assertThat(member.isActive()).isTrue();
        assertThat(member.isAdmin()).isFalse();
    }

    @Test
    void 가입_직후에는_로그인_정지_탈퇴_일시가_없다() {
        var member = Member.signUp();

        assertThat(member.getLastLoginAt()).isNull();
        assertThat(member.getSuspendedAt()).isNull();
        assertThat(member.getWithdrawnAt()).isNull();
    }

}
