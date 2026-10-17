package org.kwakmunsu.auddum.member.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.domain.AuthProvider;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.MemberAuth;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.CommandService;

@RequiredArgsConstructor
@CommandService
public class MemberCommandService {

    private final MemberJpaRepository memberRepository;
    private final MemberAuthJpaRepository memberAuthRepository;

    /**
     * 소셜 계정으로 로그인한 회원을 돌려준다. 연동된 회원이 없으면(처음이거나 탈퇴한 계정) ACTIVE 회원으로 새로 가입시킨다.
     * 제공자가 달라도 같은 흐름이고, 같은 사람이 다른 제공자로 로그인하면 별개의 회원이다.
     * 정지 회원도 로그인할 수 있다. 마지막 로그인 시각을 기록한다.
     */
    public Member login(AuthProvider provider, String providerUserId) {
        Member member = memberAuthRepository.findByProviderAndProviderUserId(provider, providerUserId)
            .map(MemberAuth::getMember)
            .orElseGet(() -> signUp(provider, providerUserId));

        member.recordLogin(LocalDateTime.now(TimeConfig.KST));

        return member;
    }

    private Member signUp(AuthProvider provider, String providerUserId) {
        Member member = memberRepository.save(Member.signUp());
        memberAuthRepository.save(MemberAuth.of(member, provider, providerUserId));

        return member;
    }

}
