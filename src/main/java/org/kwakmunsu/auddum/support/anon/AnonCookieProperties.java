package org.kwakmunsu.auddum.support.anon;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 익명 쿠키 설정. 서명 키는 32바이트 이상이어야 하며 운영에서는 환경 변수(ANON_COOKIE_SECRET)로만 주입한다.
 * 키가 없거나 짧으면 기동하지 못한다.
 */
@ConfigurationProperties(prefix = "auddum.anon-cookie")
public record AnonCookieProperties(String secret) {

    static final int MIN_SECRET_BYTES = 32;

    public AnonCookieProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("익명 쿠키 서명 키는 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
    }

}
