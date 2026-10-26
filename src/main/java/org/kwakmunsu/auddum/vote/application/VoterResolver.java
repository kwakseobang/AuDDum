package org.kwakmunsu.auddum.vote.application;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.support.anon.AnonIdentity;
import org.kwakmunsu.auddum.support.anon.AnonIdentityResolver;
import org.kwakmunsu.auddum.vote.application.dto.VoterCredential;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.stereotype.Component;

/**
 * 참여 요청의 참여자를 정한다(인증-중복-참여 §2). 로그인한 회원이면 정상 회원인지 확인하고(정지 403, 탈퇴·없음 401) 회원 ID를 쓰며 익명 쿠키는 보지 않는다.
 * 비회원이면 쿠키 해석(유효하면 그 UUID, 없거나 위조면 발급)을 {@link AnonIdentityResolver}에 시키고 결과를 {@link Voter}로 감싼다.
 */
@RequiredArgsConstructor
@Component
public class VoterResolver {

    private final AnonIdentityResolver anonIdentityResolver;
    private final MemberReader memberReader;

    public ResolvedVoter resolve(VoterCredential credential) {
        if (credential.isMember()) {
            memberReader.getActiveMember(credential.memberId());

            return ResolvedVoter.existing(Voter.member(credential.memberId()));
        }

        AnonIdentity identity = anonIdentityResolver.resolve(credential.anonCookieValue());

        return new ResolvedVoter(Voter.anonymous(new AnonId(identity.id())), identity.newCookieValue());
    }

    /**
     * 조회용으로 참여자를 찾는다. 쓰기와 달리 쿠키를 읽기만 하고 발급하지 않으며, 쿠키가 없거나 위조된 경우는 참여자가 없는 것으로 본다.
     * 회원은 상태를 확인하지 않는다(정지 회원도 조회할 수 있다).
     */
    public Optional<Voter> find(VoterCredential credential) {
        if (credential.isMember()) {
            return Optional.of(Voter.member(credential.memberId()));
        }

        return anonIdentityResolver.find(credential.anonCookieValue())
            .map(id -> Voter.anonymous(new AnonId(id)));
    }

}
