package org.kwakmunsu.auddum.vote.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 익명 쿠키 값(인증-중복-참여 §2) 발급·검증. 값은 {@code v1.<UUID>.<서명>}이고, 서명은 {@code v1.<UUID>}를 서버 비밀 키로 만든
 * HMAC-SHA256(Base64url, 패딩 없음)이다. 형식이 틀리거나 서명이 맞지 않으면 무효이고, 예외를 던지지 않는다.
 */
@EnableConfigurationProperties(AnonCookieProperties.class)
@Component
public class AnonCookieProvider {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = ".";
    private static final int PARTS = 3;
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public AnonCookieProvider(AnonCookieProperties properties) {
        this.key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** 새 UUID로 서명된 쿠키를 만든다. */
    public AnonCookie issue() {
        AnonId anonId = new AnonId(UUID.randomUUID());
        String payload = VERSION + SEPARATOR + anonId.value();

        return new AnonCookie(anonId, payload + SEPARATOR + sign(payload));
    }

    /** 쿠키 값을 검증하고 유효 여부와 무효 사유(형식 오류, 서명 불일치)를 돌려준다. */
    public Verification check(String cookieValue) {
        if (cookieValue == null) {
            return new Verification.Malformed();
        }

        String[] parts = cookieValue.split("\\.", -1);
        if (parts.length != PARTS || !VERSION.equals(parts[0]) || parts[2].isEmpty()) {
            return new Verification.Malformed();
        }

        UUID uuid = parseUuid(parts[1]);
        if (uuid == null) {
            return new Verification.Malformed();
        }

        byte[] expected = sign(parts[0] + SEPARATOR + parts[1]).getBytes(StandardCharsets.UTF_8);
        byte[] actual = parts[2].getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            return new Verification.SignatureMismatch();
        }

        return new Verification.Valid(new AnonId(uuid));
    }

    /** 소문자 표준 형식(8-4-4-4-12)만 UUID로 받는다. 서버가 발급한 값은 항상 이 형식이다. */
    private UUID parseUuid(String raw) {
        try {
            UUID uuid = UUID.fromString(raw);

            return uuid.toString().equals(raw) ? uuid : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("익명 쿠키 서명을 만들 수 없습니다.", e);
        }
    }

    /** 쿠키 검증 결과. */
    public sealed interface Verification {

        record Valid(AnonId anonId) implements Verification {

        }

        record Malformed() implements Verification {

        }

        record SignatureMismatch() implements Verification {

        }

    }

}
