package org.kwakmunsu.auddum.auth.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.domain.MemberWithdrawnEvent;
import org.kwakmunsu.auddum.support.stereotype.CommandService;
import org.springframework.context.event.EventListener;

/** 회원이 탈퇴하면 그 회원의 리프레시 토큰을 모두 삭제한다. 탈퇴와 같은 트랜잭션에서 실행된다. */
@RequiredArgsConstructor
@CommandService
public class MemberWithdrawnEventHandler {

    private final RefreshTokenWriter refreshTokenWriter;

    @EventListener
    public void deleteRefreshTokens(MemberWithdrawnEvent event) {
        refreshTokenWriter.deleteAllByMemberId(event.memberId());
    }

}
