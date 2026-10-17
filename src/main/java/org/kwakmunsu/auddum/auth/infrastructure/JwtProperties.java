package org.kwakmunsu.auddum.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. 서명 키는 32바이트 이상이어야 하며 운영에서는 환경 변수(JWT_SECRET)로만 주입한다. 만료 시간은 설계서에서 확정했으므로 설정으로 빼지 않는다.
 */
@ConfigurationProperties(prefix = "auddum.auth.jwt")
public record JwtProperties(String secret) {

}
