package org.kwakmunsu.auddum.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.auth.domain.RefreshToken;
import org.kwakmunsu.auddum.auth.infrastructure.RefreshTokenJpaRepository;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 인증-중복-참여 교체·폐기·재발급 판단 순서, ERD refresh_token(해시만 저장, 회원당 여러 건), 예외-정책 P-20·27(동시 로그인 허용).
 *
 * 가정한 이름:
 * - RefreshTokenService#issue(Long memberId, Role role, LocalDateTime now) -> IssuedTokens(accessToken(), refreshToken())
 * - RefreshTokenService#reissue(String refreshToken, LocalDateTime now) -> IssuedTokens (역할은 DB의 회원에서 읽는다)
 * - RefreshTokenService#logout(String refreshToken, LocalDateTime now)
 * - RefreshTokenJpaRepository#findAllByMemberId(Long), #findByTokenHash(String), RefreshToken#getTokenHash()/getRevokedAt()/getExpiresAt()
 * - token_hash는 원문의 SHA-256 소문자 16진수(CHAR(64))로 가정한다.
 *
 * 재사용 감지는 예외를 던지면서도 전체 폐기가 커밋되어야 하므로(noRollbackFor) 테스트를 트랜잭션으로 감싸지 않는다. 대신 만든 회원·토큰을 직접 지운다.
 * 폐기가 롤백되는 구현은 이 테스트에서 실패해야 한다.
 */
@RequiredArgsConstructor
class RefreshTokenServiceTest extends IntegrationTestSupport {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0, 0);

    final RefreshTokenService service;
    final RefreshTokenJpaRepository refreshTokenJpaRepository;
    final MemberJpaRepository memberJpaRepository;
    final JdbcTemplate jdbcTemplate;

    final List<Long> createdMemberIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdMemberIds.forEach(id -> refreshTokenJpaRepository.deleteAll(refreshTokenJpaRepository.findAllByMemberId(id)));
        createdMemberIds.forEach(memberJpaRepository::deleteById);
        createdMemberIds.clear();
    }

    @Test
    void 발급하면_원문이_아닌_해시만_저장하고_만료는_14일_뒤다() {
        var memberId = createMember();

        var tokens = service.issue(memberId, Role.MEMBER, NOW);

        var stored = refreshTokenJpaRepository.findAllByMemberId(memberId);
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getTokenHash()).hasSize(64).isNotEqualTo(tokens.refreshToken()).isEqualTo(sha256Hex(tokens.refreshToken()));
        assertThat(stored.getFirst().getExpiresAt()).isEqualTo(NOW.plusDays(14));
        assertThat(stored.getFirst().getRevokedAt()).isNull();
    }

    @Test
    void 재발급하면_새_리프레시_토큰을_저장하고_이전_토큰의_revoked_at을_채운다() {
        var memberId = createMember();
        var old = service.issue(memberId, Role.MEMBER, NOW);
        var reissueAt = NOW.plusMinutes(5);

        var renewed = service.reissue(old.refreshToken(), reissueAt);

        assertThat(renewed.refreshToken()).isNotEqualTo(old.refreshToken());
        assertThat(renewed.accessToken()).isNotBlank();
        assertThat(find(old.refreshToken()).getRevokedAt()).isEqualTo(reissueAt);
        assertThat(find(renewed.refreshToken()).getRevokedAt()).isNull();
    }

    @Test
    void 폐기_후_10초_이내에_같은_토큰이_다시_오면_허용하고_전체_폐기하지_않는다() {
        var memberId = createMember();
        var old = service.issue(memberId, Role.MEMBER, NOW);
        var firstAt = NOW.plusMinutes(5);
        service.reissue(old.refreshToken(), firstAt);

        var second = service.reissue(old.refreshToken(), firstAt.plusSeconds(10));

        assertThat(second.refreshToken()).isNotBlank();
        assertThat(find(second.refreshToken()).getRevokedAt()).isNull();
    }

    @Test
    void 폐기_후_10초를_넘겨_같은_토큰이_오면_401이고_그_회원의_토큰을_모두_폐기한다() {
        var memberId = createMember();
        var otherDevice = service.issue(memberId, Role.MEMBER, NOW);
        var old = service.issue(memberId, Role.MEMBER, NOW);
        var firstAt = NOW.plusMinutes(5);
        var renewed = service.reissue(old.refreshToken(), firstAt);
        var reuseAt = firstAt.plusSeconds(10).plusNanos(1_000_000);

        assertThatThrownBy(() -> service.reissue(old.refreshToken(), reuseAt))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());

        assertThat(refreshTokenJpaRepository.findAllByMemberId(memberId)).hasSize(3).allMatch(RefreshToken::isRevoked);
        assertThat(find(otherDevice.refreshToken()).isRevoked()).isTrue();
        assertThat(find(renewed.refreshToken()).isRevoked()).isTrue();
    }

    @Test
    void 재사용을_감지해도_다른_회원의_토큰은_폐기하지_않는다() {
        var memberId = createMember();
        var otherMemberId = createMember();
        var old = service.issue(memberId, Role.MEMBER, NOW);
        var otherToken = service.issue(otherMemberId, Role.MEMBER, NOW);
        service.reissue(old.refreshToken(), NOW.plusMinutes(5));

        assertThatThrownBy(() -> service.reissue(old.refreshToken(), NOW.plusMinutes(6))).isInstanceOf(AuddumException.class);

        assertThat(find(otherToken.refreshToken()).isRevoked()).isFalse();
    }

    @Test
    void 전체_폐기된_뒤에는_재사용_직전에_발급된_새_토큰으로도_재발급할_수_없다() {
        var memberId = createMember();
        var old = service.issue(memberId, Role.MEMBER, NOW);
        var renewed = service.reissue(old.refreshToken(), NOW.plusMinutes(5));
        assertThatThrownBy(() -> service.reissue(old.refreshToken(), NOW.plusMinutes(6))).isInstanceOf(AuddumException.class);

        assertThatThrownBy(() -> service.reissue(renewed.refreshToken(), NOW.plusMinutes(7).plusSeconds(30)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void DB에_없는_리프레시_토큰은_401이다() {
        var memberId = createMember();
        var token = service.issue(memberId, Role.MEMBER, NOW);
        refreshTokenJpaRepository.deleteAll(refreshTokenJpaRepository.findAllByMemberId(memberId));

        assertThatThrownBy(() -> service.reissue(token.refreshToken(), NOW.plusMinutes(1)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 만료된_리프레시_토큰은_401이다() {
        var memberId = createMember();
        var token = service.issue(memberId, Role.MEMBER, NOW);

        assertThatThrownBy(() -> service.reissue(token.refreshToken(), NOW.plusDays(14).plusSeconds(1)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 로그아웃은_현재_기기의_토큰만_폐기하고_다른_기기는_유지한다() {
        var memberId = createMember();
        var phone = service.issue(memberId, Role.MEMBER, NOW);
        var laptop = service.issue(memberId, Role.MEMBER, NOW);

        service.logout(phone.refreshToken(), NOW.plusMinutes(1));

        assertThat(find(phone.refreshToken()).getRevokedAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(find(laptop.refreshToken()).isRevoked()).isFalse();
    }

    @Test
    void 리프레시_토큰이_없거나_비어_있으면_재발급은_401이다() {
        assertThatThrownBy(() -> service.reissue(null, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
        assertThatThrownBy(() -> service.reissue(" ", NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 리프레시_토큰이_없거나_비어_있어도_로그아웃은_예외_없이_끝난다() {
        assertThatCode(() -> service.logout(null, NOW)).doesNotThrowAnyException();
        assertThatCode(() -> service.logout(" ", NOW)).doesNotThrowAnyException();
    }

    @Test
    void 모르는_토큰으로_로그아웃해도_예외_없이_끝난다() {
        assertThatCode(() -> service.logout("unknown-refresh-token", NOW)).doesNotThrowAnyException();
    }

    @Test
    void 회원이_없는_토큰으로_재발급하면_401이다() {
        var memberId = createMember();
        var token = service.issue(memberId, Role.MEMBER, NOW);
        deleteMemberRowOnly(memberId);

        assertThatThrownBy(() -> service.reissue(token.refreshToken(), NOW.plusMinutes(1)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 정지_상태인_회원의_토큰으로_재발급할_수_있다() {
        var memberId = createMember();
        var token = service.issue(memberId, Role.MEMBER, NOW);
        var member = memberJpaRepository.findById(memberId).orElseThrow();
        MemberFixture.changeStatus(member, MemberStatus.SUSPENDED);
        memberJpaRepository.save(member);

        var renewed = service.reissue(token.refreshToken(), NOW.plusMinutes(1));

        assertThat(renewed.accessToken()).isNotBlank();
        assertThat(renewed.refreshToken()).isNotBlank().isNotEqualTo(token.refreshToken());
        assertThat(find(token.refreshToken()).getRevokedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    void 액세스_토큰으로_재발급하면_401이다() {
        var memberId = createMember();
        var tokens = service.issue(memberId, Role.MEMBER, NOW);

        assertThatThrownBy(() -> service.reissue(tokens.accessToken(), NOW.plusMinutes(1)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    /** FK(fk_refresh_token_member) 때문에 토큰이 남은 회원은 지울 수 없어, H2의 참조 무결성 검사를 잠시 끄고 회원 행만 지운다. */
    private void deleteMemberRowOnly(Long memberId) {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            memberJpaRepository.deleteById(memberId);
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private Long createMember() {
        var id = memberJpaRepository.save(Member.signUp()).getId();
        createdMemberIds.add(id);

        return id;
    }

    private RefreshToken find(String rawToken) {
        return refreshTokenJpaRepository.findByTokenHash(sha256Hex(rawToken)).orElseThrow();
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
