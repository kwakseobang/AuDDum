package org.kwakmunsu.auddum.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.auth.application.RefreshTokenService;
import org.kwakmunsu.auddum.auth.infrastructure.RefreshTokenJpaRepository;
import org.kwakmunsu.auddum.member.domain.AuthProvider;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 통합 테스트(H2). 도메인-모델 Member(withdraw, 상태 전이), ERD member/member_auth/refresh_token(P-04·P-14),
 * 인증-중복-참여 "탈퇴하면 그 회원의 리프레시 토큰을 모두 삭제한다".
 *
 * 가정한 이름:
 * - MemberCommandService#withdraw(Long memberId) (반환값에 의존하지 않는다)
 * - Member#getStatus(), #getWithdrawnAt() / PollJpaRepository#count()
 * - 리프레시 토큰 삭제는 탈퇴와 같은 트랜잭션에서 일어나고(메커니즘은 이벤트든 직접 호출이든 무관), 이 테스트는 @Transactional로 롤백한다.
 */
@RequiredArgsConstructor
@Transactional
class MemberWithdrawTest extends IntegrationTestSupport {

    static final String KAKAO_ID = "1234567890";
    static final ZoneId KST = TimeConfig.KST;
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0, 0);

    final MemberCommandService memberCommandService;
    final RefreshTokenService refreshTokenService;
    final MemberJpaRepository memberJpaRepository;
    final MemberAuthJpaRepository memberAuthJpaRepository;
    final RefreshTokenJpaRepository refreshTokenJpaRepository;
    final PollJpaRepository pollJpaRepository;
    final EntityManager entityManager;

    @Test
    void 탈퇴하면_상태가_WITHDRAWN이_되고_탈퇴_일시가_기록된다() {
        var memberId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();

        var before = LocalDateTime.now(TimeConfig.KST).truncatedTo(ChronoUnit.MICROS);
        memberCommandService.withdraw(memberId);
        var after = LocalDateTime.now(TimeConfig.KST).plusNanos(1_000);
        flushAndClear();

        var member = memberJpaRepository.findById(memberId).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getWithdrawnAt()).isBetween(before, after);
    }

    @Test
    void 정지_회원도_탈퇴하면_WITHDRAWN이_된다() {
        var memberId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        MemberFixture.changeStatus(memberJpaRepository.findById(memberId).orElseThrow(), MemberStatus.SUSPENDED);
        flushAndClear();

        var before = LocalDateTime.now(TimeConfig.KST).truncatedTo(ChronoUnit.MICROS);
        memberCommandService.withdraw(memberId);
        var after = LocalDateTime.now(TimeConfig.KST).plusNanos(1_000);
        flushAndClear();

        var member = memberJpaRepository.findById(memberId).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getWithdrawnAt()).isBetween(before, after);
    }

    @Test
    void 탈퇴하면_소셜_연동_정보가_삭제된다() {
        var memberId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        flushAndClear();

        memberCommandService.withdraw(memberId);
        flushAndClear();

        assertThat(memberAuthJpaRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, KAKAO_ID)).isEmpty();
    }

    @Test
    void 탈퇴하면_그_회원의_리프레시_토큰이_모두_삭제되고_다른_회원의_토큰은_남는다() {
        var memberId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        var otherId = memberCommandService.login(AuthProvider.KAKAO, "9999999999").getId();
        refreshTokenService.issue(memberId, Role.MEMBER, NOW);
        refreshTokenService.issue(memberId, Role.MEMBER, NOW);
        refreshTokenService.issue(otherId, Role.MEMBER, NOW);
        flushAndClear();

        memberCommandService.withdraw(memberId);
        flushAndClear();

        assertThat(refreshTokenJpaRepository.findAllByMemberId(memberId)).isEmpty();
        assertThat(refreshTokenJpaRepository.findAllByMemberId(otherId)).hasSize(1);
    }

    @Test
    void 탈퇴해도_회원이_쓴_투표_행은_그대로_남는다() {
        var memberId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        var pollId = pollJpaRepository.save(Poll.create(memberId, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", Category.SOME, List.of("가", "나"), null, NOW)).getId();
        flushAndClear();

        memberCommandService.withdraw(memberId);
        flushAndClear();

        var poll = pollJpaRepository.findById(pollId).orElseThrow();
        assertThat(poll.getAuthorId()).isEqualTo(memberId);
        assertThat(memberJpaRepository.findById(memberId)).isPresent();
    }

    @Test
    void 탈퇴한_카카오_계정으로_다시_로그인하면_이전_회원과_다른_새_ACTIVE_회원이_된다() {
        var withdrawnId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        memberCommandService.withdraw(withdrawnId);
        flushAndClear();

        var newId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        flushAndClear();

        assertThat(newId).isNotEqualTo(withdrawnId);
        assertThat(memberJpaRepository.findById(newId).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(memberJpaRepository.findById(withdrawnId).orElseThrow().getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
    }

    @Test
    void 탈퇴한_회원의_이전_리프레시_토큰으로_재발급하면_401이다() {
        var memberId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        var tokens = refreshTokenService.issue(memberId, Role.MEMBER, NOW);
        flushAndClear();
        memberCommandService.withdraw(memberId);
        flushAndClear();

        assertThatThrownBy(() -> refreshTokenService.reissue(tokens.refreshToken(), NOW.plusMinutes(1)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

}
