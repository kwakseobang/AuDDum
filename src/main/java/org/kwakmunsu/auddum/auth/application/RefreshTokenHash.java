package org.kwakmunsu.auddum.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 리프레시 토큰의 저장용 해시(SHA-256). 원문은 저장하지 않고 이 해시로만 찾는다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RefreshTokenHash {

    static String of(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 환경이다", e);
        }
    }

}
