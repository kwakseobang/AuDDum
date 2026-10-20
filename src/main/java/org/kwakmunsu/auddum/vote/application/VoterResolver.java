package org.kwakmunsu.auddum.vote.application;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.vote.application.dto.VoterCredential;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookie;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider.Verification;
import org.springframework.stereotype.Component;

/**
 * 참여 요청의 참여자를 정한다(인증-중복-참여 §2). 로그인한 회원이면 정상 회원인지 확인하고(정지 403, 탈퇴·없음 401) 회원 ID를 쓰며 익명 쿠키는 보지 않는다.
 * 비회원이면 유효한 쿠키의 UUID를 쓰고, 쿠키가 없거나 위조된 경우에는 새로 발급한다. 위조는 오류 없이 처리하고 WARN으로 남긴다.
 * 위조된 값은 외부 입력이라 로그에 넣지 않고 사유만 남긴다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class VoterResolver {

    private final AnonCookieProvider anonCookieProvider;
    private final MemberReader memberReader;

    public ResolvedVoter resolve(VoterCredential credential) {
        if (credential.isMember()) {
            memberReader.getActiveMember(credential.memberId());

            return ResolvedVoter.existing(Voter.member(credential.memberId()));
        }
        if (!credential.hasAnonCookie()) {
            return issueNew();
        }

        return switch (anonCookieProvider.check(credential.anonCookieValue())) {
            case Verification.Valid valid -> ResolvedVoter.existing(Voter.anonymous(valid.anonId()));
            case Verification.Malformed _ -> forged("형식 오류");
            case Verification.SignatureMismatch _ -> forged("서명 불일치");
        };
    }

    /**
     * 조회용으로 참여자를 찾는다. 쓰기와 달리 쿠키를 읽기만 하고 발급하지 않으며, 쿠키가 없거나 위조된 경우는 참여자가 없는 것으로 본다.
     * 위조 로그도 남기지 않는다(조회는 호출이 많다). 회원은 상태를 확인하지 않는다(정지 회원도 조회할 수 있다).
     */
    public Optional<Voter> find(VoterCredential credential) {
        if (credential.isMember()) {
            return Optional.of(Voter.member(credential.memberId()));
        }
        if (!credential.hasAnonCookie()) {
            return Optional.empty();
        }

        return switch (anonCookieProvider.check(credential.anonCookieValue())) {
            case Verification.Valid valid -> Optional.of(Voter.anonymous(valid.anonId()));
            case Verification.Malformed _, Verification.SignatureMismatch _ -> Optional.empty();
        };
    }

    private ResolvedVoter forged(String reason) {
        log.warn("[ANON_COOKIE_FORGED] 위조 의심 익명 쿠키를 무효로 처리하고 새로 발급했다 (reason={})", reason);

        return issueNew();
    }

    private ResolvedVoter issueNew() {
        AnonCookie cookie = anonCookieProvider.issue();

        return ResolvedVoter.issued(Voter.anonymous(cookie.anonId()), cookie.value());
    }

}
