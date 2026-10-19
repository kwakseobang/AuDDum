package org.kwakmunsu.auddum.vote.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.support.stereotype.ApplicationService;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookie;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider.Verification;

/**
 * 참여 요청의 참여자를 정한다(인증-중복-참여 §2). 로그인한 회원이면 회원 ID를 쓰고 익명 쿠키는 보지 않는다.
 * 비회원이면 유효한 쿠키의 UUID를 쓰고, 쿠키가 없거나 위조된 경우에는 새로 발급한다. 위조는 오류 없이 처리하고 WARN으로 남긴다.
 * 위조된 값은 외부 입력이라 로그에 넣지 않고 사유만 남긴다.
 */
@Slf4j
@RequiredArgsConstructor
@ApplicationService
public class AnonVoterResolver {

    private final AnonCookieProvider anonCookieProvider;

    /**
     * @param memberId    로그인한 회원 ID. 비회원이면 null
     * @param cookieValue {@code auddum_anon} 쿠키 값. 없으면 null
     */
    public ResolvedVoter resolve(Long memberId, String cookieValue) {
        if (memberId != null) {
            return new ResolvedVoter(Voter.member(memberId), null);
        }
        if (cookieValue == null || cookieValue.isEmpty()) {
            return issueNew();
        }

        return switch (anonCookieProvider.check(cookieValue)) {
            case Verification.Valid valid -> new ResolvedVoter(Voter.anonymous(valid.anonId()), null);
            case Verification.Malformed malformed -> forged("형식 오류");
            case Verification.SignatureMismatch mismatch -> forged("서명 불일치");
        };
    }

    private ResolvedVoter forged(String reason) {
        log.warn("[ANON_COOKIE_FORGED] 위조 의심 익명 쿠키를 무효로 처리하고 새로 발급했다 (reason={})", reason);

        return issueNew();
    }

    private ResolvedVoter issueNew() {
        AnonCookie cookie = anonCookieProvider.issue();

        return new ResolvedVoter(Voter.anonymous(cookie.anonId()), cookie.value());
    }

}
