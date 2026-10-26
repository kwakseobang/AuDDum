package org.kwakmunsu.auddum.support.anon;

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
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider.Verification;
import org.slf4j.LoggerFactory;

/**
 * 익명 쿠키 해석 단위 테스트. 기준은 인증-중복-참여 §2 위조·변조 처리(위조는 오류 없이 새로 발급하고 WARN, 원문은 로그에 넣지 않음)
 * 와 설계-결정 "위조 쿠키 로그"이다.
 */
class AnonIdentityResolverTest {

    private static final String SECRET = "test-anon-cookie-secret-0123456789-abcdef";
    private static final String OTHER_SECRET = "another-anon-cookie-secret-9876543210-zyxwvu";

    private final AnonCookieProvider anonCookieProvider = new AnonCookieProvider(new AnonCookieProperties(SECRET));
    private final AnonIdentityResolver resolver = new AnonIdentityResolver(anonCookieProvider);

    private final Logger anonLogger = (Logger) LoggerFactory.getLogger("org.kwakmunsu.auddum.support.anon");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
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

    // ---- resolve: 쿠키 없음 ----

    @Test
    void 쿠키가_없으면_새로_발급하고_로그를_남기지_않는다() {
        var identity = resolver.resolve(null);

        assertThat(identity.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(identity.newCookieValue())).isEqualTo(new Verification.Valid(identity.id()));
        assertThat(appender.list).isEmpty();
    }

    @Test
    void 빈_문자열_쿠키는_쿠키_없음으로_보고_로그_없이_새로_발급한다() {
        var identity = resolver.resolve("");

        assertThat(identity.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(identity.newCookieValue())).isEqualTo(new Verification.Valid(identity.id()));
        assertThat(appender.list).isEmpty();
    }

    // ---- resolve: 유효 쿠키 ----

    @Test
    void 유효한_쿠키면_쿠키의_UUID를_쓰고_다시_발급하지_않는다() {
        var cookie = anonCookieProvider.issue();

        var identity = resolver.resolve(cookie.value());

        assertThat(identity.id()).isEqualTo(cookie.id());
        assertThat(identity.newCookieValue()).isNull();
    }

    @Test
    void 유효한_쿠키면_로그를_남기지_않는다() {
        resolver.resolve(anonCookieProvider.issue().value());

        assertThat(appender.list).isEmpty();
    }

    // ---- resolve: 위조 ----

    @Test
    void 위조된_쿠키면_예외_없이_새_UUID를_발급한다() {
        var victim = anonCookieProvider.issue();

        var identity = resolver.resolve("v1." + victim.id() + ".forged");

        assertThat(identity.id()).isNotEqualTo(victim.id());
        assertThat(identity.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(identity.newCookieValue())).isEqualTo(new Verification.Valid(identity.id()));
    }

    @Test
    void 서명이_틀린_쿠키는_WARN_한_건으로_서명_불일치를_남긴다() {
        resolver.resolve("v1." + UUID.randomUUID() + ".forgedsig");

        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(logText(event)).contains("[AnonIdentityResolver]").contains("reason=서명 불일치");
    }

    @Test
    void 형식이_틀린_쿠키는_WARN_한_건으로_형식_오류를_남긴다() {
        resolver.resolve("garbage-cookie");

        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(logText(event)).contains("[AnonIdentityResolver]").contains("reason=형식 오류");
    }

    @Test
    void 서명이_틀린_쿠키의_원문_UUID_서명은_로그에_들어가지_않는다() {
        var forgedUuid = UUID.randomUUID();
        var forgedValue = "v1." + forgedUuid + ".forgedsig";

        resolver.resolve(forgedValue);

        assertThat(appender.list).isNotEmpty();
        appender.list.forEach(event -> assertThat(logText(event))
                .doesNotContain(forgedValue)
                .doesNotContain(forgedUuid.toString())
                .doesNotContain("forgedsig"));
    }

    @Test
    void 다른_키로_서명한_쿠키도_원문을_로그에_남기지_않는다() {
        var other = new AnonCookieProvider(new AnonCookieProperties(OTHER_SECRET)).issue();

        resolver.resolve(other.value());

        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(logText(event)).doesNotContain(other.value()).doesNotContain(other.id().toString());
    }

    @Test
    void 형식이_틀린_쿠키의_원문은_로그에_들어가지_않는다() {
        resolver.resolve("garbage-cookie");

        assertThat(appender.list).isNotEmpty();
        appender.list.forEach(event -> assertThat(logText(event)).doesNotContain("garbage-cookie"));
    }

    @Test
    void 위조_쿠키를_두_번_보내면_매번_다른_UUID를_받는다() {
        var first = resolver.resolve("garbage");
        var second = resolver.resolve("garbage");

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    // ---- find: 조회용 ----

    @Test
    void find는_유효한_쿠키면_그_UUID를_재발급_없이_돌려준다() {
        var cookie = anonCookieProvider.issue();

        var found = resolver.find(cookie.value());

        assertThat(found).contains(cookie.id());
    }

    @Test
    void find는_쿠키가_없거나_빈_문자열이면_비어_있고_발급도_로그도_없다() {
        assertThat(resolver.find(null)).isEmpty();
        assertThat(resolver.find("")).isEmpty();
        assertThat(appender.list).isEmpty();
    }

    @Test
    void find는_위조된_쿠키면_비어_있고_발급도_로그도_없다() {
        assertThat(resolver.find("garbage-cookie")).isEmpty();
        assertThat(resolver.find("v1." + UUID.randomUUID() + ".forgedsig")).isEmpty();
        assertThat(appender.list).isEmpty();
    }

}
