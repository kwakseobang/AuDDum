package org.kwakmunsu.auddum.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.kwakmunsu.auddum.support.jpa.BaseEntity;

/**
 * 리프레시 토큰 한 건(기기 하나). 원문이 아니라 해시만 저장하고, 재발급하면 폐기 시각만 채워 남겨 둔다.
 * 폐기된 토큰이 다시 오면 폐기 후 10초까지는 동시 재발급으로 보고 허용한다.
 */
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class RefreshToken extends BaseEntity {

    private static final Duration GRACE = Duration.ofSeconds(10);

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public static RefreshToken issue(Long memberId, String tokenHash, LocalDateTime expiresAt) {
        RefreshToken token = new RefreshToken();

        token.memberId = memberId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;

        return token;
    }

    /** 이미 폐기된 토큰이면 처음 폐기한 시각을 그대로 둔다. */
    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** 폐기 후 10초 이내(경계 포함)면 true. 폐기되지 않은 토큰은 false다. */
    public boolean isWithinGrace(LocalDateTime now) {
        return revokedAt != null && !now.isAfter(revokedAt.plus(GRACE));
    }

}
