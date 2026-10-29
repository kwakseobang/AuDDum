package org.kwakmunsu.auddum.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 토큰 표: 액세스 {sub, role, iat, exp} 30분, 리프레시 {sub, jti, iat, exp} 14일, 리프레시에는 role이 없다.
 * 가정한 이름: JwtTokenProvider(JwtProperties), createAccessToken(Long, Role, LocalDateTime now), createRefreshToken(Long, LocalDateTime now),
 * parseAccessToken(String, LocalDateTime now) -> 레코드(memberId(), role()), parseRefreshToken(String, LocalDateTime now) -> 레코드(memberId(), jti()).
 * 무효 토큰은 AuddumException(UNAUTHENTICATED). 만료 판단은 시스템 시계가 아니라 넘긴 now 기준이다.
 */
class JwtTokenProviderTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0, 0);
    static final String SECRET = "test-secret-key-test-secret-key-test-secret-key-0123";

    final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET));
    final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void 액세스_토큰은_sub와_role을_담고_30분_뒤_만료된다() {
        var token = provider.createAccessToken(42L, Role.ADMIN, NOW);

        var payload = payload(token);
        assertThat(payload.get("sub").asString()).isEqualTo("42");
        assertThat(payload.get("role").asString()).isEqualTo("ADMIN");
        assertThat(payload.get("iat").asLong()).isEqualTo(epoch(NOW));
        assertThat(payload.get("exp").asLong()).isEqualTo(epoch(NOW) + 30 * 60);
    }

    @Test
    void 리프레시_토큰은_sub와_jti를_담고_14일_뒤_만료되며_role은_없다() {
        var token = provider.createRefreshToken(42L, NOW);

        var payload = payload(token);
        assertThat(payload.get("sub").asString()).isEqualTo("42");
        assertThat(payload.get("jti").asString()).isNotBlank();
        assertThat(payload.has("role")).isFalse();
        assertThat(payload.get("exp").asLong()).isEqualTo(epoch(NOW) + 14L * 24 * 60 * 60);
    }

    @Test
    void 같은_시각에_발급해도_리프레시_토큰마다_jti가_달라_토큰이_다르다() {
        var first = provider.createRefreshToken(42L, NOW);
        var second = provider.createRefreshToken(42L, NOW);

        assertThat(first).isNotEqualTo(second);
        assertThat(provider.parseRefreshToken(first, NOW).jti()).isNotEqualTo(provider.parseRefreshToken(second, NOW).jti());
    }

    @Test
    void 액세스_토큰을_파싱하면_회원_ID와_권한을_돌려준다() {
        var token = provider.createAccessToken(42L, Role.MEMBER, NOW);

        var claims = provider.parseAccessToken(token, NOW.plusMinutes(29));

        assertThat(claims.memberId()).isEqualTo(42L);
        assertThat(claims.role()).isEqualTo(Role.MEMBER);
    }

    @Test
    void 만료된_액세스_토큰은_UNAUTHENTICATED로_거절한다() {
        var token = provider.createAccessToken(42L, Role.MEMBER, NOW);

        assertThatThrownBy(() -> provider.parseAccessToken(token, NOW.plusMinutes(30).plusSeconds(1)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 서명이_다른_토큰은_UNAUTHENTICATED로_거절한다() {
        var other = new JwtTokenProvider(new JwtProperties("another-secret-key-another-secret-key-another-key-99"));
        var token = other.createAccessToken(42L, Role.MEMBER, NOW);

        assertThatThrownBy(() -> provider.parseAccessToken(token, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 액세스_토큰은_category가_access이고_리프레시_토큰은_refresh다() {
        var access = provider.createAccessToken(42L, Role.MEMBER, NOW);
        var refresh = provider.createRefreshToken(42L, NOW);

        assertThat(payload(access).get("category").asString()).isEqualTo("access");
        assertThat(payload(refresh).get("category").asString()).isEqualTo("refresh");
    }

    @Test
    void 리프레시_토큰을_액세스_토큰으로_파싱하면_UNAUTHENTICATED로_거절한다() {
        var refresh = provider.createRefreshToken(42L, NOW);

        assertThatThrownBy(() -> provider.parseAccessToken(refresh, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 액세스_토큰을_리프레시_토큰으로_파싱하면_UNAUTHENTICATED로_거절한다() {
        var access = provider.createAccessToken(42L, Role.MEMBER, NOW);

        assertThatThrownBy(() -> provider.parseRefreshToken(access, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 서명_키가_48바이트_이상이어도_액세스_토큰과_리프레시_토큰의_alg는_HS256이다() {
        assertThat(SECRET.getBytes(StandardCharsets.UTF_8).length).isGreaterThanOrEqualTo(48);

        var access = provider.createAccessToken(42L, Role.MEMBER, NOW);
        var refresh = provider.createRefreshToken(42L, NOW);

        assertThat(header(access).get("alg").asString()).isEqualTo("HS256");
        assertThat(header(refresh).get("alg").asString()).isEqualTo("HS256");
    }

    private tools.jackson.databind.JsonNode header(String jwt) {
        var json = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[0]), StandardCharsets.UTF_8);

        return jsonMapper.readTree(json);
    }

    private tools.jackson.databind.JsonNode payload(String jwt) {
        var json = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);

        return jsonMapper.readTree(json);
    }

    private long epoch(LocalDateTime time) {
        return time.atZone(TimeConfig.KST).toEpochSecond();
    }

}
