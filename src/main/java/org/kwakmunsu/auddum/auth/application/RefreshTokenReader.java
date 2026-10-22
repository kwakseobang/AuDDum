package org.kwakmunsu.auddum.auth.application;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.auth.domain.RefreshToken;
import org.kwakmunsu.auddum.auth.infrastructure.RefreshTokenJpaRepository;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 읽기. 토큰 원문으로 저장된 기록을 찾는다(저장은 해시로만 하므로 원문을 해시해서 찾는다).
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여한다.
 */
@RequiredArgsConstructor
@Component
class RefreshTokenReader {

    private final RefreshTokenJpaRepository refreshTokenRepository;

    /** 같은 토큰으로 동시에 재발급해도 한 요청씩 처리되도록 행을 잠그고 읽는다. */
    Optional<RefreshToken> findForUpdate(String refreshToken) {
        return refreshTokenRepository.findForUpdate(RefreshTokenHash.of(refreshToken));
    }

    Optional<RefreshToken> find(String refreshToken) {
        return refreshTokenRepository.findByTokenHash(RefreshTokenHash.of(refreshToken));
    }

}
