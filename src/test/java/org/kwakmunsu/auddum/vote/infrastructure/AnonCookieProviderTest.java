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

    @ParameterizedTest
    @ValueSource(strings = {"다른 쿠키의 서명", "UUID만 변경", "서명 잘라냄", "다른 서명 키"})
    void 서명이_맞지_않는_쿠키는_SignatureMismatch로_무효다(String kind) {
        var cookie = anonCookieProvider.issue();
        var other = anonCookieProvider.issue();
        var forged = switch (kind) {
            case "다른 쿠키의 서명" -> "v1." + cookie.anonId().value() + "." + signatureOf(other.value());
            case "UUID만 변경" -> "v1." + UUID.randomUUID() + "." + signatureOf(cookie.value());
            case "서명 잘라냄" -> cookie.value().substring(0, cookie.value().length() - 1);
            case "다른 서명 키" -> new AnonCookieProvider(new AnonCookieProperties(OTHER_SECRET)).issue().value();
            default -> throw new IllegalArgumentException(kind);
        };

        assertThat(anonCookieProvider.check(forged)).isInstanceOf(Verification.SignatureMismatch.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"서명 비어 있음", "서명 없음", "버전 v2", "부분 네 개", "UUID 형식 아님", "대문자 UUID"})
    void 형식이_맞지_않는_쿠키는_Malformed로_무효다(String kind) {
        var cookie = anonCookieProvider.issue();
        var uuid = cookie.anonId().value();
        var forged = switch (kind) {
            case "서명 비어 있음" -> "v1." + uuid + ".";
            case "서명 없음" -> "v1." + uuid;
            case "버전 v2" -> "v2" + cookie.value().substring(2);
            case "부분 네 개" -> cookie.value() + ".extra";
            case "UUID 형식 아님" -> "v1.not-a-uuid.abcdef";
            // 서명이 맞아도 정규형(소문자) UUID가 아니면 형식 오류다
            case "대문자 UUID" -> "v1." + uuid.toString().toUpperCase() + "." + signatureOf(cookie.value());
            default -> throw new IllegalArgumentException(kind);
        };

        assertThat(anonCookieProvider.check(forged)).isInstanceOf(Verification.Malformed.class);
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
