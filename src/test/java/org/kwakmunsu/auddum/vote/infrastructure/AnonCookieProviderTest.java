package org.kwakmunsu.auddum.vote.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider.Verification;

class AnonCookieProviderTest {

    private static final String SECRET = "test-anon-cookie-secret-0123456789-abcdef";
    private static final String OTHER_SECRET = "other-anon-cookie-secret-9876543210-fedcba";

    private final AnonCookieProvider anonCookieProvider = new AnonCookieProvider(new AnonCookieProperties(SECRET));

    // ---- 발급 ----

    @Test
    void 발급한_쿠키_값은_버전_UUID_서명_세_부분이다() {
        var cookie = anonCookieProvider.issue();

        var parts = cookie.value().split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isEqualTo("v1");
        assertThat(UUID.fromString(parts[1])).isEqualTo(cookie.anonId().value());
        assertThat(parts[2]).isNotBlank();
    }

    @Test
    void 발급할_때마다_다른_UUID가_나온다() {
        assertThat(anonCookieProvider.issue().anonId()).isNotEqualTo(anonCookieProvider.issue().anonId());
    }

    // ---- 검증: 유효 ----

    @Test
    void 발급한_쿠키는_검증을_통과하고_같은_UUID를_돌려준다() {
        var cookie = anonCookieProvider.issue();

        assertThat(anonCookieProvider.check(cookie.value())).isEqualTo(new Verification.Valid(cookie.anonId()));
    }

    // ---- 검증: 위조·변조 ----

    @Test
    void 서명을_다른_쿠키의_서명으로_바꾸면_무효다() {
        var cookie = anonCookieProvider.issue();
        var other = anonCookieProvider.issue();
        var forged = "v1." + cookie.anonId().value() + "." + signatureOf(other.value());

        assertThat(anonCookieProvider.check(forged)).isInstanceOf(Verification.SignatureMismatch.class);
    }

    @Test
    void UUID만_바꾸고_서명을_그대로_두면_무효다() {
        var cookie = anonCookieProvider.issue();
        var forged = "v1." + UUID.randomUUID() + "." + signatureOf(cookie.value());

        assertThat(anonCookieProvider.check(forged)).isInstanceOf(Verification.SignatureMismatch.class);
    }

    @Test
    void 서명을_잘라내면_무효다() {
        var cookie = anonCookieProvider.issue();
        var forged = cookie.value().substring(0, cookie.value().length() - 1);

        assertThat(anonCookieProvider.check(forged)).isInstanceOf(Verification.SignatureMismatch.class);
    }

    @Test
    void 서명이_비어_있으면_무효다() {
        var cookie = anonCookieProvider.issue();

        var result = anonCookieProvider.check("v1." + cookie.anonId().value() + ".");

        assertThat(result).isInstanceOf(Verification.Malformed.class);
    }

    @Test
    void 서명이_없으면_무효다() {
        var cookie = anonCookieProvider.issue();

        var result = anonCookieProvider.check("v1." + cookie.anonId().value());

        assertThat(result).isInstanceOf(Verification.Malformed.class);
    }

    @Test
    void 버전이_v1이_아니면_무효다() {
        var cookie = anonCookieProvider.issue();
        var forged = "v2" + cookie.value().substring(2);

        assertThat(anonCookieProvider.check(forged)).isInstanceOf(Verification.Malformed.class);
    }

    @Test
    void 부분이_네_개면_무효다() {
        var cookie = anonCookieProvider.issue();

        assertThat(anonCookieProvider.check(cookie.value() + ".extra")).isInstanceOf(Verification.Malformed.class);
    }

    @Test
    void 다른_서명_키로_발급한_쿠키는_무효다() {
        var cookie = new AnonCookieProvider(new AnonCookieProperties(OTHER_SECRET)).issue();

        assertThat(anonCookieProvider.check(cookie.value())).isInstanceOf(Verification.SignatureMismatch.class);
    }

    @Test
    void UUID_자리가_UUID_형식이_아니면_무효다() {
        assertThat(anonCookieProvider.check("v1.not-a-uuid.abcdef")).isInstanceOf(Verification.Malformed.class);
    }

    @Test
    void 대문자_UUID처럼_정규형이_아니면_서명이_맞아도_형식_오류다() {
        var cookie = anonCookieProvider.issue();
        var upper = "v1." + cookie.anonId().value().toString().toUpperCase() + "." + signatureOf(cookie.value());

        assertThat(anonCookieProvider.check(upper)).isInstanceOf(Verification.Malformed.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "garbage", "...", "v1..", "v1"})
    void 형식이_틀린_값은_예외_없이_무효다(String raw) {
        assertThat(anonCookieProvider.check(raw)).isInstanceOf(Verification.Malformed.class);
    }

    private static String signatureOf(String cookieValue) {
        return cookieValue.substring(cookieValue.lastIndexOf('.') + 1);
    }

}
