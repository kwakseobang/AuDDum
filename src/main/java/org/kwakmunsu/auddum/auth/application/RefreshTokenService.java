package org.kwakmunsu.auddum.auth.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.auth.domain.RefreshToken;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.application.MemberReader;
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
    private final RefreshTokenReader refreshTokenReader;
    private final RefreshTokenWriter refreshTokenWriter;
    private final MemberReader memberReader;

    /**
     * 로그인한 회원에게 새 액세스·리프레시 토큰을 발급한다. 리프레시 토큰은 기기마다 한 건씩 쌓인다.
     */
    public IssuedTokens issue(Long memberId, Role role, LocalDateTime now) {
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId, now);

        refreshTokenWriter.save(memberId, refreshToken, now.plus(JwtTokenProvider.REFRESH_TTL));

        return IssuedTokens.builder()
            .accessToken(jwtTokenProvider.createAccessToken(memberId, role, now))
            .refreshToken(refreshToken)
            .build();
    }

    /**
     * 리프레시 토큰으로 새 토큰을 발급하고 이전 토큰을 폐기한다. 토큰이 없거나 무효하면 401이다. 재사용을 감지해 던지는 401에서도 전체 폐기는 커밋되어야 하므로 예외에 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = AuddumException.class)
    public IssuedTokens reissue(String refreshToken, LocalDateTime now) {
        jwtTokenProvider.parseRefreshToken(refreshToken, now);

        RefreshToken stored = refreshTokenReader.findForUpdate(refreshToken)
            .orElseThrow(() -> new AuddumException(DefaultErrorCode.UNAUTHENTICATED));
        replace(stored, now);
        // 회원이 없어 아래에서 401이 되어도 위의 폐기는 커밋된다(noRollbackFor). 결과가 같아 의도한 동작이다.

        Member member = memberReader.getNotWithdrawnMember(stored.getMemberId());

        return issue(member.getId(), member.getRole(), now);
    }

    /**
     * 현재 기기의 리프레시 토큰만 폐기한다. 토큰이 없거나 모르는 토큰이어도 이미 로그아웃된 것과 같으므로 오류로 보지 않는다.
     */
    public void logout(String refreshToken, LocalDateTime now) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        // 모르는 토큰이면 이미 로그아웃된 것과 같으므로 읽어서 있을 때만 폐기한다. 폐기는 영속 엔티티의 상태 변경이라 이 트랜잭션이 끝날 때 반영된다.
        refreshTokenReader.find(refreshToken)
            .ifPresent(stored -> refreshTokenWriter.revoke(stored, now));
    }

    /**
     * 재발급에 쓰인 토큰을 교체 대상으로 처리한다. 아직 쓰이지 않았으면 폐기하고, 이미 폐기됐어도 10초 유예 안(동시 재발급)이면 그대로 둔다. 유예를 넘겼으면 재사용으로 보고 그 회원의 토큰을 모두 폐기한 뒤 거절한다.
     */
    private void replace(RefreshToken stored, LocalDateTime now) {
        if (!stored.isRevoked()) {
            refreshTokenWriter.revoke(stored, now);
            return;
        }
        if (stored.isWithinGrace(now)) {
            return;
        }

        refreshTokenWriter.revokeAll(stored.getMemberId(), now);
        log.warn("[RefreshTokenService] 폐기된 리프레시 토큰 재사용 감지, 회원 토큰 전체 폐기 - memberId={}", stored.getMemberId());

        throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED);
    }

}
