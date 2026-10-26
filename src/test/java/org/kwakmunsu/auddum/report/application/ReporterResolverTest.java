package org.kwakmunsu.auddum.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.report.application.dto.ReporterCredential;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.kwakmunsu.auddum.support.anon.AnonCookieProperties;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider.Verification;
import org.kwakmunsu.auddum.support.anon.AnonIdentityResolver;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;

/**
 * 신고자 결정 단위 테스트. 기준은 인증-중복-참여 §2, API-명세 §5(회원 상태: 정지 403, 탈퇴·없음 401). 쿠키 해석 자체는 AnonIdentityResolverTest가 검증한다.
 */
class ReporterResolverTest {

    private static final Long MEMBER_ID = 7L;
    private static final String SECRET = "test-anon-cookie-secret-0123456789-abcdef";

    private final AnonCookieProvider anonCookieProvider = new AnonCookieProvider(new AnonCookieProperties(SECRET));
    private final MemberJpaRepository memberRepository = mock(MemberJpaRepository.class);
    private final MemberReader memberReader = new MemberReader(memberRepository, mock(MemberAuthJpaRepository.class));
    private final ReporterResolver resolver = new ReporterResolver(new AnonIdentityResolver(anonCookieProvider), memberReader);

    @BeforeEach
    void stubMember() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(MemberFixture.createMember()));
    }

    // ---- 회원 상태 확인 ----

    @Test
    void 정상_회원이면_회원_ID가_신고자이고_쿠키를_발급하지_않는다() {
        var resolved = resolver.resolve(new ReporterCredential(MEMBER_ID, null));

        assertThat(resolved.reporter()).isEqualTo(Reporter.member(MEMBER_ID));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 정지_회원이면_MEMBER_SUSPENDED다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(
                MemberFixture.changeStatus(MemberFixture.createMember(), MemberStatus.SUSPENDED)));

        assertThatThrownBy(() -> resolver.resolve(new ReporterCredential(MEMBER_ID, null)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
    }

    @Test
    void 탈퇴한_회원이면_UNAUTHENTICATED다() {
        var withdrawn = MemberFixture.createMember();
        withdrawn.withdraw(LocalDateTime.of(2026, 10, 26, 12, 0));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> resolver.resolve(new ReporterCredential(MEMBER_ID, null)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 없는_회원이면_UNAUTHENTICATED다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(new ReporterCredential(MEMBER_ID, null)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    // ---- 회원은 쿠키를 보지 않는다 ----

    @Test
    void 회원은_유효한_익명_쿠키가_있어도_회원_ID가_신고자이고_발급이_없다() {
        var cookie = anonCookieProvider.issue();

        var resolved = resolver.resolve(new ReporterCredential(MEMBER_ID, cookie.value()));

        assertThat(resolved.reporter()).isEqualTo(Reporter.member(MEMBER_ID));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 회원은_위조된_익명_쿠키가_있어도_회원_ID가_신고자이고_발급이_없다() {
        var resolved = resolver.resolve(new ReporterCredential(MEMBER_ID, "v1." + UUID.randomUUID() + ".forged"));

        assertThat(resolved.reporter()).isEqualTo(Reporter.member(MEMBER_ID));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 비회원은_회원_조회를_하지_않는다() {
        resolver.resolve(new ReporterCredential(null, null));
        resolver.resolve(new ReporterCredential(null, anonCookieProvider.issue().value()));

        verifyNoInteractions(memberRepository);
    }

    // ---- Reporter 포장 ----

    @Test
    void 유효한_쿠키면_쿠키의_UUID가_익명_신고자이고_다시_발급하지_않는다() {
        var cookie = anonCookieProvider.issue();

        var resolved = resolver.resolve(new ReporterCredential(null, cookie.value()));

        assertThat(resolved.reporter()).isEqualTo(Reporter.anonymous(cookie.id()));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 쿠키가_없으면_새_익명_신고자를_만들고_newCookieValue를_채운다() {
        var resolved = resolver.resolve(new ReporterCredential(null, null));

        assertThat(resolved.reporter().memberId()).isNull();
        assertThat(resolved.reporter().anonId()).isNotNull();
        assertThat(resolved.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(resolved.newCookieValue()))
                .isEqualTo(new Verification.Valid(resolved.reporter().anonId()));
    }

    @Test
    void 위조된_쿠키면_다른_사람의_신고자가_아닌_새_신고자로_발급한다() {
        var victim = anonCookieProvider.issue();

        var resolved = resolver.resolve(new ReporterCredential(null, "v1." + victim.id() + ".forged"));

        assertThat(resolved.reporter()).isNotEqualTo(Reporter.anonymous(victim.id()));
        assertThat(resolved.newCookieValue()).isNotNull();
    }

}
