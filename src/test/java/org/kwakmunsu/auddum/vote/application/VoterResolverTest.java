package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.anonymous;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.member;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.memberWithCookie;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.noCredential;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.tamperSignature;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.anon.AnonCookieProperties;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider.Verification;
import org.kwakmunsu.auddum.support.anon.AnonIdentityResolver;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.slf4j.LoggerFactory;

/**
 * 참여자 결정 단위 테스트. 쿠키 해석과 위조 로그는 AnonIdentityResolverTest가 검증하고, 여기서는 회원 상태 확인·회원 우선·Voter 포장·find 위임만 본다.
 * 회원이면 먼저 정상 회원인지 확인한다(없거나 탈퇴 401 UNAUTHENTICATED, 정지 403 MEMBER_SUSPENDED).
 * MemberReader는 실제 객체를 쓰고 repository만 목으로 둬 상태별 예외 규칙을 그대로 태운다.
 * 기준은 익명 식별 쿠키, 참여 순서(회원 상태 -> 참여자 결정).
 */
class VoterResolverTest {

    private static final Long MEMBER_ID = 7L;
    private static final String SECRET = "test-anon-cookie-secret-0123456789-abcdef";

    private final AnonCookieProvider anonCookieProvider = new AnonCookieProvider(new AnonCookieProperties(SECRET));
    private final MemberJpaRepository memberRepository = mock(MemberJpaRepository.class);
    private final MemberReader memberReader = new MemberReader(memberRepository, mock(MemberAuthJpaRepository.class));
    private final VoterResolver resolver = new VoterResolver(new AnonIdentityResolver(anonCookieProvider), memberReader);

    private final Logger anonLogger = (Logger) LoggerFactory.getLogger("org.kwakmunsu.auddum.support.anon");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(MemberFixture.createMember()));
        originalLevel = anonLogger.getLevel();
        anonLogger.setLevel(Level.ALL);
        appender.start();
        anonLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        anonLogger.detachAppender(appender);
        appender.stop();
        anonLogger.setLevel(originalLevel);
    }

    private String logText(ILoggingEvent event) {
        var arguments = event.getArgumentArray() == null ? "" : Arrays.toString(event.getArgumentArray());
        var throwable = event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage();
        return event.getFormattedMessage() + " " + arguments + " " + throwable;
    }

    // ---- 회원 상태 확인 (정지 403, 탈퇴·없음 401) ----

    @Test
    void 정상_회원이면_회원_ID가_참여자다() {
        var resolved = resolver.resolve(member(MEMBER_ID));

        assertThat(resolved.voter()).isEqualTo(Voter.member(MEMBER_ID));
    }

    @Test
    void 정지_회원이면_MEMBER_SUSPENDED다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(
                MemberFixture.changeStatus(MemberFixture.createMember(), MemberStatus.SUSPENDED)));

        assertThatThrownBy(() -> resolver.resolve(member(MEMBER_ID)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
    }

    @Test
    void 탈퇴한_회원이면_UNAUTHENTICATED다() {
        var withdrawn = MemberFixture.createMember();
        withdrawn.withdraw(LocalDateTime.of(2026, 10, 19, 12, 0));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> resolver.resolve(member(MEMBER_ID)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 없는_회원이면_UNAUTHENTICATED다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(member(MEMBER_ID)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 정지_회원이_위조된_쿠키를_보내도_MEMBER_SUSPENDED이고_로그가_없다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(
                MemberFixture.changeStatus(MemberFixture.createMember(), MemberStatus.SUSPENDED)));

        assertThatThrownBy(() -> resolver.resolve(memberWithCookie(MEMBER_ID, "v1." + UUID.randomUUID() + ".forged")))
                .isInstanceOf(AuddumException.class)
                .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
        assertThat(appender.list).isEmpty();
    }

    @Test
    void 탈퇴한_회원이_위조된_쿠키를_보내도_UNAUTHENTICATED이고_로그가_없다() {
        var withdrawn = MemberFixture.createMember();
        withdrawn.withdraw(LocalDateTime.of(2026, 10, 19, 12, 0));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> resolver.resolve(memberWithCookie(MEMBER_ID, "garbage")))
                .isInstanceOf(AuddumException.class)
                .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
        assertThat(appender.list).isEmpty();
    }

    @Test
    void 비회원은_회원_조회를_하지_않는다() {
        resolver.resolve(noCredential());
        resolver.resolve(anonymous(anonCookieProvider.issue().value()));

        verifyNoInteractions(memberRepository);
    }

    // ---- 회원 우선 ----

    @Test
    void 로그인한_회원은_회원_ID가_참여자이고_쿠키를_발급하지_않는다() {
        var resolved = resolver.resolve(member(MEMBER_ID));

        assertThat(resolved.voter()).isEqualTo(Voter.member(MEMBER_ID));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 로그인한_회원은_유효한_익명_쿠키가_있어도_회원_ID가_참여자다() {
        var cookie = anonCookieProvider.issue();

        var resolved = resolver.resolve(memberWithCookie(MEMBER_ID, cookie.value()));

        assertThat(resolved.voter()).isEqualTo(Voter.member(MEMBER_ID));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 로그인한_회원은_위조된_익명_쿠키를_보지_않아_발급도_로그도_없다() {
        var resolved = resolver.resolve(memberWithCookie(MEMBER_ID, "v1." + UUID.randomUUID() + ".forged"));

        assertThat(resolved.voter()).isEqualTo(Voter.member(MEMBER_ID));
        assertThat(resolved.newCookieValue()).isNull();
        assertThat(appender.list).isEmpty();
    }

    // ---- Voter 포장 ----

    @Test
    void 비회원이고_쿠키가_없으면_새_익명_참여자로_포장하고_쿠키를_발급한다() {
        var resolved = resolver.resolve(noCredential());

        assertThat(resolved.voter().anonId()).isNotNull();
        assertThat(resolved.voter().memberId()).isNull();
        assertThat(resolved.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(resolved.newCookieValue()))
                .isEqualTo(new Verification.Valid(resolved.voter().anonId().value()));
    }

    @Test
    void 유효한_쿠키면_쿠키의_UUID가_참여자이고_다시_발급하지_않는다() {
        var cookie = anonCookieProvider.issue();

        var resolved = resolver.resolve(anonymous(cookie.value()));

        assertThat(resolved.voter()).isEqualTo(Voter.anonymous(new AnonId(cookie.id())));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 위조된_쿠키면_다른_사람의_참여자가_아닌_새_참여자로_발급한다() {
        var victim = anonCookieProvider.issue();

        var resolved = resolver.resolve(anonymous("v1." + victim.id() + ".forged"));

        assertThat(resolved.voter()).isNotEqualTo(Voter.anonymous(new AnonId(victim.id())));
        assertThat(resolved.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(resolved.newCookieValue()))
                .isEqualTo(new Verification.Valid(resolved.voter().anonId().value()));
    }

    // ---- find 위임 (조회용) ----

    @Test
    void find는_회원이면_상태를_확인하지_않고_회원_참여자를_돌려준다() {
        var found = resolver.find(member(MEMBER_ID));

        assertThat(found).contains(Voter.member(MEMBER_ID));
        verifyNoInteractions(memberRepository);
    }

    @Test
    void find는_유효한_쿠키면_그_UUID의_익명_참여자를_돌려준다() {
        var cookie = anonCookieProvider.issue();

        var found = resolver.find(anonymous(cookie.value()));

        assertThat(found).contains(Voter.anonymous(new AnonId(cookie.id())));
    }

    @Test
    void find는_쿠키가_없거나_위조되었으면_비어_있고_로그도_없다() {
        assertThat(resolver.find(noCredential())).isEmpty();
        assertThat(resolver.find(anonymous("garbage"))).isEmpty();
        assertThat(resolver.find(anonymous(tamperSignature(anonCookieProvider.issue().value())))).isEmpty();
        assertThat(appender.list).isEmpty();
    }

}
