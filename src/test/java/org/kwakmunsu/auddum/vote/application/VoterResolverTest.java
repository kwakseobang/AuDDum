package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.anonymous;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.member;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.memberWithCookie;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.noCredential;
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
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProperties;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider.Verification;
import org.slf4j.LoggerFactory;

/**
 * 참여자 결정 단위 테스트. 회원이면 먼저 정상 회원인지 확인한다(없거나 탈퇴 401 UNAUTHENTICATED, 정지 403 MEMBER_SUSPENDED).
 * MemberReader는 실제 객체를 쓰고 repository만 목으로 둬 상태별 예외 규칙을 그대로 태운다.
 * 기준은 인증-중복-참여 §2, API-명세 §4 참여 순서(회원 상태 -> 참여자 결정).
 */
class VoterResolverTest {

    private static final Long MEMBER_ID = 7L;
    private static final String SECRET = "test-anon-cookie-secret-0123456789-abcdef";
    private static final String OTHER_SECRET = "another-anon-cookie-secret-9876543210-zyxwvu";

    private final AnonCookieProvider anonCookieProvider = new AnonCookieProvider(new AnonCookieProperties(SECRET));
    private final MemberJpaRepository memberRepository = mock(MemberJpaRepository.class);
    private final MemberReader memberReader = new MemberReader(memberRepository);
    private final VoterResolver resolver = new VoterResolver(anonCookieProvider, memberReader);

    private final Logger voteLogger = (Logger) LoggerFactory.getLogger("org.kwakmunsu.auddum.vote");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(MemberFixture.createMember()));
        originalLevel = voteLogger.getLevel();
        voteLogger.setLevel(Level.ALL);
        appender.start();
        voteLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        voteLogger.detachAppender(appender);
        appender.stop();
        voteLogger.setLevel(originalLevel);
    }

    private String logText(ILoggingEvent event) {
        var arguments = event.getArgumentArray() == null ? "" : Arrays.toString(event.getArgumentArray());
        var throwable = event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage();
        return event.getFormattedMessage() + " " + arguments + " " + throwable;
    }

    // ---- 회원 상태 확인 (API-명세 §4 "회원 상태: 정지 403, 탈퇴·없음 401") ----

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

    // ---- 쿠키 없음 ----

    @Test
    void 비회원이고_쿠키가_없으면_새_익명_참여자를_만들고_쿠키를_발급한다() {
        var resolved = resolver.resolve(noCredential());

        assertThat(resolved.voter().anonId()).isNotNull();
        assertThat(resolved.voter().memberId()).isNull();
        assertThat(resolved.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(resolved.newCookieValue()))
                .isEqualTo(new Verification.Valid(resolved.voter().anonId()));
    }

    @Test
    void 빈_문자열_쿠키는_쿠키_없음으로_보고_로그_없이_새로_발급한다() {
        var resolved = resolver.resolve(anonymous(""));

        assertThat(resolved.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(resolved.newCookieValue()))
                .isEqualTo(new Verification.Valid(resolved.voter().anonId()));
        assertThat(appender.list).isEmpty();
    }

    @Test
    void 쿠키가_없는_것은_위조가_아니어서_로그를_남기지_않는다() {
        resolver.resolve(noCredential());

        assertThat(appender.list).isEmpty();
    }

    // ---- 유효 쿠키 ----

    @Test
    void 유효한_쿠키면_쿠키의_UUID가_참여자이고_다시_발급하지_않는다() {
        var cookie = anonCookieProvider.issue();

        var resolved = resolver.resolve(anonymous(cookie.value()));

        assertThat(resolved.voter()).isEqualTo(Voter.anonymous(cookie.anonId()));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 유효한_쿠키면_로그를_남기지_않는다() {
        resolver.resolve(anonymous(anonCookieProvider.issue().value()));

        assertThat(appender.list).isEmpty();
    }

    // ---- 위조 쿠키 ----

    @Test
    void 위조된_쿠키면_예외_없이_새_UUID를_발급한다() {
        var victim = anonCookieProvider.issue();
        var forged = "v1." + victim.anonId().value() + ".forged";

        var resolved = resolver.resolve(anonymous(forged));

        assertThat(resolved.voter().anonId()).isNotEqualTo(victim.anonId());
        assertThat(resolved.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(resolved.newCookieValue()))
                .isEqualTo(new Verification.Valid(resolved.voter().anonId()));
    }

    @Test
    void 위조된_쿠키의_UUID로는_다른_사람의_참여자를_흉내_낼_수_없다() {
        var victim = anonCookieProvider.issue();

        var resolved = resolver.resolve(anonymous("v1." + victim.anonId().value() + ".forged"));

        assertThat(resolved.voter()).isNotEqualTo(Voter.anonymous(victim.anonId()));
    }

    @Test
    void 서명이_틀린_쿠키는_WARN_한_건으로_서명_불일치를_남긴다() {
        var forgedUuid = UUID.randomUUID();

        resolver.resolve(anonymous("v1." + forgedUuid + ".forgedsig"));

        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(logText(event)).contains("[ANON_COOKIE_FORGED]").contains("reason=서명 불일치");
    }

    @Test
    void 형식이_틀린_쿠키는_WARN_한_건으로_형식_오류를_남긴다() {
        resolver.resolve(anonymous("garbage-cookie"));

        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(logText(event)).contains("[ANON_COOKIE_FORGED]").contains("reason=형식 오류");
    }

    @Test
    void 서명이_틀린_쿠키의_원문_UUID_서명은_로그에_들어가지_않는다() {
        var forgedUuid = UUID.randomUUID();
        var forgedValue = "v1." + forgedUuid + ".forgedsig";

        resolver.resolve(anonymous(forgedValue));

        assertThat(appender.list).isNotEmpty();
        appender.list.forEach(event -> assertThat(logText(event))
                .doesNotContain(forgedValue)
                .doesNotContain(forgedUuid.toString())
                .doesNotContain("forgedsig"));
    }

    @Test
    void 다른_키로_서명한_쿠키도_원문을_로그에_남기지_않는다() {
        var other = new AnonCookieProvider(new AnonCookieProperties(OTHER_SECRET)).issue();

        resolver.resolve(anonymous(other.value()));

        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(logText(event))
                .doesNotContain(other.value())
                .doesNotContain(other.anonId().value().toString());
    }

    @Test
    void 형식이_틀린_쿠키의_원문은_로그에_들어가지_않는다() {
        resolver.resolve(anonymous("garbage-cookie"));

        assertThat(appender.list).isNotEmpty();
        appender.list.forEach(event -> assertThat(logText(event)).doesNotContain("garbage-cookie"));
    }

    @Test
    void 위조_쿠키를_두_번_보내면_매번_다른_UUID를_받는다() {
        var first = resolver.resolve(anonymous("garbage"));
        var second = resolver.resolve(anonymous("garbage"));

        assertThat(first.voter()).isNotEqualTo(second.voter());
    }

}
