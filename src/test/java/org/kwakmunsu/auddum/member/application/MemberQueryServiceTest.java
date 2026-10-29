package org.kwakmunsu.auddum.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 조회 통합 테스트(H2). API-명세 GET /members/me, 인증-중복-참여 회원 상태 확인, 예외-정책 P-22.
 * 가정한 이름: MemberQueryService#getMyInfo(Long memberId) -> Member.
 * 정지 행위가 아직 없어 Member의 status 필드를 직접 바꿔 만든다.
 */
@RequiredArgsConstructor
@Transactional
class MemberQueryServiceTest extends IntegrationTestSupport {

    static final long NOT_EXISTING_MEMBER_ID = 999_999_999L;

    final MemberQueryService memberQueryService;
    final MemberJpaRepository memberJpaRepository;

    @Test
    void 정상_회원의_내_정보는_ACTIVE_MEMBER와_가입_시각이다() {
        var saved = saveMember(MemberStatus.ACTIVE);

        var member = memberQueryService.getMyInfo(saved.getId());

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getRole()).isEqualTo(Role.MEMBER);
        assertThat(member.getCreatedAt()).isNotNull();
    }

    @Test
    void 정지_회원도_자기_정보를_조회할_수_있다() {
        var saved = saveMember(MemberStatus.SUSPENDED);

        var member = memberQueryService.getMyInfo(saved.getId());

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
    }

    @Test
    void 탈퇴한_회원이면_401_UNAUTHENTICATED다() {
        var saved = saveMember(MemberStatus.WITHDRAWN);

        assertThatThrownBy(() -> memberQueryService.getMyInfo(saved.getId()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void DB에_없는_회원이면_401_UNAUTHENTICATED다() {
        assertThatThrownBy(() -> memberQueryService.getMyInfo(NOT_EXISTING_MEMBER_ID))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    private Member saveMember(MemberStatus status) {
        var member = MemberFixture.createMember();
        MemberFixture.changeStatus(member, status);

        return memberJpaRepository.saveAndFlush(member);
    }

}
