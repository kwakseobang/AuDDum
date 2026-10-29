package org.kwakmunsu.auddum.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.AuddumException;

/**
 * 회원 이용 정지. 기준은 도메인-모델 §3 Member 행위 표 suspend(now):
 * "ACTIVE일 때만 SUSPENDED로 바꾸고 정지 일시를 기록한다. 이미 정지·탈퇴한 회원이면 409 MEMBER_NOT_ACTIVE다." API-명세 §7 MEMBER_NOT_ACTIVE(409).
 */
class MemberSuspendTest {

    private final LocalDateTime now = LocalDateTime.now(TimeConfig.KST);

    @Test
    void ACTIVE_회원을_정지하면_SUSPENDED이고_정지_일시를_기록한다() {
        var member = Member.signUp();

        member.suspend(now);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.isSuspended()).isTrue();
        assertThat(member.getSuspendedAt()).isEqualTo(now);
    }

    @Test
    void 이미_정지된_회원을_정지하면_409_MEMBER_NOT_ACTIVE이고_정지_일시는_처음_그대로다() {
        var member = Member.signUp();
        member.suspend(now);

        assertThatThrownBy(() -> member.suspend(now.plusHours(1)))
            .isInstanceOfSatisfying(AuddumException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_ACTIVE));
        assertThat(MemberErrorCode.MEMBER_NOT_ACTIVE.getStatus().value()).isEqualTo(409);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspendedAt()).isEqualTo(now);
    }

    @Test
    void 탈퇴한_회원을_정지하면_409_MEMBER_NOT_ACTIVE이고_상태와_정지_일시가_바뀌지_않는다() {
        var member = Member.signUp();
        member.withdraw(now);

        assertThatThrownBy(() -> member.suspend(now.plusHours(1)))
            .isInstanceOfSatisfying(AuddumException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_ACTIVE));
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getSuspendedAt()).isNull();
        assertThat(member.getWithdrawnAt()).isEqualTo(now);
    }

    @Test
    void 정지해도_권한과_탈퇴_일시는_바뀌지_않는다() {
        var member = MemberFixture.createMember(Role.MEMBER);

        member.suspend(now);

        assertThat(member.getRole()).isEqualTo(Role.MEMBER);
        assertThat(member.getWithdrawnAt()).isNull();
    }

}
