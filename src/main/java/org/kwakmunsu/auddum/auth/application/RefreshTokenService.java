package org.kwakmunsu.auddum.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.auth.domain.RefreshToken;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.auth.infrastructure.RefreshTokenJpaRepository;
import org.kwakmunsu.auddum.member.application.MemberQueryService;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.support.stereotype.CommandService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토큰 발급·재발급·로그아웃(인증-중복-참여 §1). 리프레시 토큰은 SHA-256 해시만 저장하고, 재발급할 때마다 교체한다. 폐기된 토큰이 유예(10초)를 넘겨 다시 오면 재사용으로 보고 그 회원의 토큰을 모두 폐기한다.
 */
@Slf4j
@RequiredArgsConstructor
@CommandService
public class RefreshTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final MemberQueryService memberQueryService;

    /**
     * 로그인한 회원에게 새 액세스·리프레시 토큰을 발급한다. 리프레시 토큰은 기기마다 한 건씩 쌓인다.
     */
    public IssuedTokens issue(Long memberId, Role role, LocalDateTime now) {
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId, now);
        refreshTokenRepository.save(RefreshToken.issue(
            memberId, hash(refreshToken),
            now.plus(JwtTokenProvider.REFRESH_TTL)
        ));

        return new IssuedTokens(jwtTokenProvider.createAccessToken(memberId, role, now), refreshToken);
    }

    /**
     * 리프레시 토큰으로 새 토큰을 발급하고 이전 토큰을 폐기한다. 토큰이 없거나 무효하면 401이다. 재사용을 감지해 던지는 401에서도 전체 폐기는 커밋되어야 하므로 예외에 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = AuddumException.class)
    public IssuedTokens reissue(String refreshToken, LocalDateTime now) {
        jwtTokenProvider.parseRefreshToken(refreshToken, now);

        RefreshToken stored = refreshTokenRepository.findForUpdate(hash(refreshToken))
            .orElseThrow(RefreshTokenService::unauthenticated);
        replace(stored, now);
        // 회원이 없어 아래에서 401이 되어도 위의 폐기는 커밋된다(noRollbackFor). 결과가 같아 의도한 동작이다.

        Member member = memberQueryService.findMember(stored.getMemberId())
            .orElseThrow(RefreshTokenService::unauthenticated);

        return issue(member.getId(), member.getRole(), now);
    }

    /**
     * 현재 기기의 리프레시 토큰만 폐기한다. 토큰이 없거나 모르는 토큰이어도 이미 로그아웃된 것과 같으므로 오류로 보지 않는다.
     */
    public void logout(String refreshToken, LocalDateTime now) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hash(refreshToken))
            .ifPresent(stored -> stored.revoke(now));
    }

    /**
     * 재발급에 쓰인 토큰을 교체 대상으로 처리한다. 아직 쓰이지 않았으면 폐기하고, 이미 폐기됐어도 10초 유예 안(동시 재발급)이면 그대로 둔다.
     * 유예를 넘겼으면 재사용으로 보고 그 회원의 토큰을 모두 폐기한 뒤 거절한다.
     */
    private void replace(RefreshToken stored, LocalDateTime now) {
        if (!stored.isRevoked()) {
            stored.revoke(now);
            return;
        }
        if (stored.isWithinGrace(now)) {
            return;
        }

        revokeAll(stored.getMemberId(), now);
        log.warn("[RefreshTokenService] 폐기된 리프레시 토큰이 재사용되어 회원의 토큰을 모두 폐기했다 (memberId={})", stored.getMemberId());

        throw unauthenticated();
    }

    private static AuddumException unauthenticated() {
        return new AuddumException(DefaultErrorCode.UNAUTHENTICATED);
    }

    private void revokeAll(Long memberId, LocalDateTime now) {
        refreshTokenRepository.findAllByMemberId(memberId)
            .forEach(token -> token.revoke(now));
    }

    private String hash(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 환경이다", e);
        }
    }

}
