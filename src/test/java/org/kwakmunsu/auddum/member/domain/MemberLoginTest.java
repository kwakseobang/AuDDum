package org.kwakmunsu.auddum.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.member.domain.MemberFixture.createMember;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Member.recordLogin(now)와 가입 직후 상태. */
class MemberLoginTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    @Test
    void 로그인을_기록하면_마지막_로그인_일시가_저장된다() {
        var member = createMember();

        member.recordLogin(NOW);

        assertThat(member.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void 다시_로그인하면_마지막_로그인_일시가_새_값으로_바뀐다() {
        var member = createMember();
        member.recordLogin(NOW);

        member.recordLogin(NOW.plusDays(1));

        assertThat(member.getLastLoginAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void 로그인을_기록해도_정지_회원의_상태는_바뀌지_않는다() {
        var member = createMember();
        MemberFixture.changeStatus(member, MemberStatus.SUSPENDED);

        member.recordLogin(NOW.plusHours(1));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
    }

}
