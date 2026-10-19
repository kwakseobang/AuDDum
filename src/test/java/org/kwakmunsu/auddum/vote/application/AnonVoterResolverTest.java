package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProperties;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider.Verification;
import org.slf4j.LoggerFactory;

class AnonVoterResolverTest {

    private static final Long MEMBER_ID = 7L;
    private static final String SECRET = "test-anon-cookie-secret-0123456789-abcdef";
    private static final String OTHER_SECRET = "another-anon-cookie-secret-9876543210-zyxwvu";

    private final AnonCookieProvider anonCookieProvider = new AnonCookieProvider(new AnonCookieProperties(SECRET));
    private final AnonVoterResolver resolver = new AnonVoterResolver(anonCookieProvider);

    private final Logger voteLogger = (Logger) LoggerFactory.getLogger("org.kwakmunsu.auddum.vote");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
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

    // ---- 회원 우선 ----

    @Test
    void 로그인한_회원은_회원_ID가_참여자이고_쿠키를_발급하지_않는다() {
        var resolved = resolver.resolve(MEMBER_ID, null);

        assertThat(resolved.voter()).isEqualTo(Voter.member(MEMBER_ID));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 로그인한_회원은_유효한_익명_쿠키가_있어도_회원_ID가_참여자다() {
        var cookie = anonCookieProvider.issue();

        var resolved = resolver.resolve(MEMBER_ID, cookie.value());

        assertThat(resolved.voter()).isEqualTo(Voter.member(MEMBER_ID));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 로그인한_회원은_위조된_익명_쿠키를_보지_않아_발급도_로그도_없다() {
        var resolved = resolver.resolve(MEMBER_ID, "v1." + UUID.randomUUID() + ".forged");

        assertThat(resolved.voter()).isEqualTo(Voter.member(MEMBER_ID));
        assertThat(resolved.newCookieValue()).isNull();
        assertThat(appender.list).isEmpty();
    }

    // ---- 쿠키 없음 ----

    @Test
    void 비회원이고_쿠키가_없으면_새_익명_참여자를_만들고_쿠키를_발급한다() {
        var resolved = resolver.resolve(null, null);

        assertThat(resolved.voter().anonId()).isNotNull();
        assertThat(resolved.voter().memberId()).isNull();
        assertThat(resolved.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(resolved.newCookieValue()))
                .isEqualTo(new Verification.Valid(resolved.voter().anonId()));
    }

    @Test
    void 빈_문자열_쿠키는_쿠키_없음으로_보고_로그_없이_새로_발급한다() {
        var resolved = resolver.resolve(null, "");

        assertThat(resolved.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(resolved.newCookieValue()))
                .isEqualTo(new Verification.Valid(resolved.voter().anonId()));
        assertThat(appender.list).isEmpty();
    }

    @Test
    void 쿠키가_없는_것은_위조가_아니어서_로그를_남기지_않는다() {
        resolver.resolve(null, null);

        assertThat(appender.list).isEmpty();
    }

    // ---- 유효 쿠키 ----

    @Test
    void 유효한_쿠키면_쿠키의_UUID가_참여자이고_다시_발급하지_않는다() {
        var cookie = anonCookieProvider.issue();

        var resolved = resolver.resolve(null, cookie.value());

        assertThat(resolved.voter()).isEqualTo(Voter.anonymous(cookie.anonId()));
        assertThat(resolved.newCookieValue()).isNull();
    }

    @Test
    void 유효한_쿠키면_로그를_남기지_않는다() {
        resolver.resolve(null, anonCookieProvider.issue().value());

        assertThat(appender.list).isEmpty();
    }

    // ---- 위조 쿠키 ----

    @Test
    void 위조된_쿠키면_예외_없이_새_UUID를_발급한다() {
        var victim = anonCookieProvider.issue();
        var forged = "v1." + victim.anonId().value() + ".forged";

        var resolved = resolver.resolve(null, forged);

        assertThat(resolved.voter().anonId()).isNotEqualTo(victim.anonId());
        assertThat(resolved.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(resolved.newCookieValue()))
                .isEqualTo(new Verification.Valid(resolved.voter().anonId()));
    }

    @Test
    void 위조된_쿠키의_UUID로는_다른_사람의_참여자를_흉내_낼_수_없다() {
        var victim = anonCookieProvider.issue();

        var resolved = resolver.resolve(null, "v1." + victim.anonId().value() + ".forged");

        assertThat(resolved.voter()).isNotEqualTo(Voter.anonymous(victim.anonId()));
    }

    @Test
    void 서명이_틀린_쿠키는_WARN_한_건으로_서명_불일치를_남긴다() {
        var forgedUuid = UUID.randomUUID();

        resolver.resolve(null, "v1." + forgedUuid + ".forgedsig");

        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(logText(event)).contains("[ANON_COOKIE_FORGED]").contains("reason=서명 불일치");
    }

    @Test
    void 형식이_틀린_쿠키는_WARN_한_건으로_형식_오류를_남긴다() {
        resolver.resolve(null, "garbage-cookie");

        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(logText(event)).contains("[ANON_COOKIE_FORGED]").contains("reason=형식 오류");
    }

    @Test
    void 서명이_틀린_쿠키의_원문_UUID_서명은_로그에_들어가지_않는다() {
        var forgedUuid = UUID.randomUUID();
        var forgedValue = "v1." + forgedUuid + ".forgedsig";

        resolver.resolve(null, forgedValue);

        assertThat(appender.list).isNotEmpty();
        appender.list.forEach(event -> assertThat(logText(event))
                .doesNotContain(forgedValue)
                .doesNotContain(forgedUuid.toString())
                .doesNotContain("forgedsig"));
    }

    @Test
    void 다른_키로_서명한_쿠키도_원문을_로그에_남기지_않는다() {
        var other = new AnonCookieProvider(new AnonCookieProperties(OTHER_SECRET)).issue();

        resolver.resolve(null, other.value());

        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(logText(event))
                .doesNotContain(other.value())
                .doesNotContain(other.anonId().value().toString());
    }

    @Test
    void 형식이_틀린_쿠키의_원문은_로그에_들어가지_않는다() {
        resolver.resolve(null, "garbage-cookie");

        assertThat(appender.list).isNotEmpty();
        appender.list.forEach(event -> assertThat(logText(event)).doesNotContain("garbage-cookie"));
    }

    @Test
    void 위조_쿠키를_두_번_보내면_매번_다른_UUID를_받는다() {
        var first = resolver.resolve(null, "garbage");
        var second = resolver.resolve(null, "garbage");

        assertThat(first.voter()).isNotEqualTo(second.voter());
    }

}
