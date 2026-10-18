package org.kwakmunsu.auddum.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 확인 통합 테스트(H2). 인증-중복-참여 §1 관리자 API 행(DB의 role이 ADMIN이고 탈퇴하지 않은 회원, 토큰 role은 판단에 쓰지 않는다).
 * MemberReader#isAdmin(Long memberId) -> boolean(던지지 않는다).
 */
@RequiredArgsConstructor
@Transactional
class MemberReaderAdminTest extends IntegrationTestSupport {

    static final long NOT_EXISTING_MEMBER_ID = 999_999_999L;

    final MemberReader memberReader;
    final MemberJpaRepository memberJpaRepository;

    @Test
    void DB의_role이_ADMIN이면_isAdmin은_true다() {
        var adminId = saveMember(Role.ADMIN);

        assertThat(memberReader.isAdmin(adminId)).isTrue();
    }

    @Test
    void DB의_role이_MEMBER면_isAdmin은_false다() {
        var memberId = saveMember(Role.MEMBER);

        assertThat(memberReader.isAdmin(memberId)).isFalse();
    }

    @Test
    void DB에_없는_회원이면_isAdmin은_던지지_않고_false다() {
        assertThat(memberReader.isAdmin(NOT_EXISTING_MEMBER_ID)).isFalse();
    }

    @Test
    void 비회원이면_isAdmin은_false다() {
        assertThat(memberReader.isAdmin(null)).isFalse();
    }

    @Test
    void 탈퇴한_ADMIN은_isAdmin이_false다() {
        var withdrawnAdminId = saveWithdrawnAdmin();

        assertThat(memberReader.isAdmin(withdrawnAdminId)).isFalse();
    }

    private Long saveWithdrawnAdmin() {
        var member = MemberFixture.createMember();
        ReflectionTestUtils.setField(member, "role", Role.ADMIN);
        member.withdraw(LocalDateTime.now());

        return memberJpaRepository.saveAndFlush(member).getId();
    }

    private Long saveMember(Role role) {
        var member = MemberFixture.createMember();
        ReflectionTestUtils.setField(member, "role", role);

        return memberJpaRepository.saveAndFlush(member).getId();
    }

}
