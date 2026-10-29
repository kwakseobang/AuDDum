package org.kwakmunsu.auddum.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 교체·폐기와 재발급 판단 순서(폐기 후 10초 이내는 허용).
 * 가정한 이름: RefreshToken.issue(Long memberId, String tokenHash, LocalDateTime expiresAt), revoke(LocalDateTime now),
 * isRevoked(), isWithinGrace(LocalDateTime now), getRevokedAt(). 유예 경계는 "10초 이내 허용, 10초 초과 재사용"으로 읽어 정확히 10초는 허용이다.
 */
class RefreshTokenTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0, 0);
    static final String HASH = "a".repeat(64);

    @Test
    void 발급한_토큰은_폐기되지_않은_상태다() {
        var token = RefreshToken.issue(1L, HASH, NOW.plusDays(14));

        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void 폐기하면_폐기_시각이_기록된다() {
        var token = RefreshToken.issue(1L, HASH, NOW.plusDays(14));

        token.revoke(NOW);

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void 폐기_후_정확히_10초까지는_유예_안이다() {
        var token = RefreshToken.issue(1L, HASH, NOW.plusDays(14));
        token.revoke(NOW);

        assertThat(token.isWithinGrace(NOW.plusSeconds(10))).isTrue();
    }

    @Test
    void 폐기_후_10초를_넘기면_유예_밖이다() {
        var token = RefreshToken.issue(1L, HASH, NOW.plusDays(14));
        token.revoke(NOW);

        assertThat(token.isWithinGrace(NOW.plusSeconds(10).plusNanos(1_000_000))).isFalse();
    }

    @Test
    void 이미_폐기된_토큰을_다시_폐기해도_최초_폐기_시각이_유지된다() {
        var token = RefreshToken.issue(1L, HASH, NOW.plusDays(14));
        token.revoke(NOW);

        token.revoke(NOW.plusMinutes(1));

        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

}
