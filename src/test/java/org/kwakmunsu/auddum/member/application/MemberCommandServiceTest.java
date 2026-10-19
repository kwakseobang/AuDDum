package org.kwakmunsu.auddum.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.domain.AuthProvider;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오 로그인의 회원 처리(가입·재로그인·정지 허용·탈퇴 후 재가입) 통합 테스트(H2).
 * 가정한 이름: MemberCommandService#login(AuthProvider, String providerUserId) -> Member,
 * MemberAuthJpaRepository#findByProviderAndProviderUserId(AuthProvider, String) -> Optional&lt;MemberAuth&gt;, MemberAuth#getMember().
 */
@RequiredArgsConstructor
@Transactional
class MemberCommandServiceTest extends IntegrationTestSupport {

    static final String KAKAO_ID = "1234567890";

    final MemberCommandService memberCommandService;
    final MemberJpaRepository memberJpaRepository;
    final MemberAuthJpaRepository memberAuthJpaRepository;
    final EntityManager entityManager;

    @Test
    void 처음_로그인하면_ACTIVE_MEMBER_회원으로_가입하고_카카오_연동_정보를_저장한다() {
        var before = LocalDateTime.now().minusMinutes(1);

        var memberId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        flushAndClear();

        var member = memberJpaRepository.findById(memberId).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getRole()).isEqualTo(Role.MEMBER);
        assertThat(member.getLastLoginAt()).isAfter(before);
        var auth = memberAuthJpaRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, KAKAO_ID).orElseThrow();
        assertThat(auth.getMember().getId()).isEqualTo(memberId);
    }

    @Test
    void 기존_회원이_다시_로그인하면_새_회원을_만들지_않고_같은_회원을_돌려준다() {
        var firstId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        flushAndClear();
        var countBefore = memberJpaRepository.count();

        var secondId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        flushAndClear();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(memberJpaRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void 기존_회원이_다시_로그인하면_마지막_로그인_일시가_갱신된다() {
        var memberId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        var oldLogin = LocalDateTime.of(2020, 1, 1, 0, 0);
        memberJpaRepository.findById(memberId).orElseThrow().recordLogin(oldLogin);
        flushAndClear();

        memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID);
        flushAndClear();

        assertThat(memberJpaRepository.findById(memberId).orElseThrow().getLastLoginAt()).isAfter(oldLogin);
    }

    @Test
    void 서로_다른_카카오_계정은_서로_다른_회원이다() {
        var first = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();

        var second = memberCommandService.login(AuthProvider.KAKAO, "9999999999").getId();

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void 정지_회원도_로그인할_수_있고_정지_상태는_유지된다() {
        var memberId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        MemberFixture.changeStatus(memberJpaRepository.findById(memberId).orElseThrow(), MemberStatus.SUSPENDED);
        flushAndClear();

        var loggedIn = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID);
        flushAndClear();

        assertThat(loggedIn.getId()).isEqualTo(memberId);
        assertThat(memberJpaRepository.findById(memberId).orElseThrow().getStatus()).isEqualTo(MemberStatus.SUSPENDED);
    }

    @Test
    void 탈퇴한_카카오_계정으로_로그인하면_이전_회원과_연결되지_않는_새_ACTIVE_회원이_된다() {
        var withdrawnId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        withdraw(withdrawnId);
        flushAndClear();

        var newId = memberCommandService.login(AuthProvider.KAKAO, KAKAO_ID).getId();
        flushAndClear();

        assertThat(newId).isNotEqualTo(withdrawnId);
        assertThat(memberJpaRepository.findById(newId).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(memberJpaRepository.findById(withdrawnId).orElseThrow().getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        var auth = memberAuthJpaRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, KAKAO_ID).orElseThrow();
        assertThat(auth.getMember().getId()).isEqualTo(newId);
    }

    /** 탈퇴 처리는 #14에서 서비스로 만든다. 그때까지는 탈퇴의 결과(상태 WITHDRAWN, 소셜 연동 삭제)를 직접 만든다. */
    private void withdraw(Long memberId) {
        var auth = memberAuthJpaRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, KAKAO_ID).orElseThrow();
        memberAuthJpaRepository.delete(auth);
        MemberFixture.changeStatus(memberJpaRepository.findById(memberId).orElseThrow(), MemberStatus.WITHDRAWN);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

}
