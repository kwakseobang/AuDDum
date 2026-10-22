package org.kwakmunsu.auddum.auth.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.auth.domain.RefreshToken;
import org.kwakmunsu.auddum.auth.infrastructure.RefreshTokenJpaRepository;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 쓰기. 원문이 아니라 해시만 저장한다.
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여한다.
 */
@RequiredArgsConstructor
@Component
class RefreshTokenWriter {

    private final RefreshTokenJpaRepository refreshTokenRepository;

    void save(Long memberId, String refreshToken, LocalDateTime expiresAt) {
        refreshTokenRepository.save(RefreshToken.issue(memberId, RefreshTokenHash.of(refreshToken), expiresAt));
    }

    /** 토큰을 폐기한다. 영속 엔티티의 상태를 바꾸는 것이라 부른 서비스의 트랜잭션이 끝날 때 반영된다(더티 체킹). */
    void revoke(RefreshToken refreshToken, LocalDateTime now) {
        refreshToken.revoke(now);
    }

    /** 회원의 리프레시 토큰을 모두 폐기한다(재사용 감지). 이미 폐기된 토큰은 그대로 둔다(도메인이 정한다). */
    void revokeAll(Long memberId, LocalDateTime now) {
        refreshTokenRepository.findAllByMemberId(memberId)
            .forEach(token -> token.revoke(now));
    }

    /** 회원의 리프레시 토큰을 모두 삭제한다. */
    void deleteAllByMemberId(Long memberId) {
        refreshTokenRepository.deleteAllByMemberId(memberId);
    }

}
