package org.kwakmunsu.auddum.member.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.domain.AuthProvider;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.MemberAuth;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.springframework.stereotype.Component;

/**
 * 회원 쓰기. 가입(회원과 소셜 연동 저장)과 소셜 연동 삭제를 맡는다.
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여한다.
 */
@RequiredArgsConstructor
@Component
class MemberWriter {

    private final MemberJpaRepository memberRepository;
    private final MemberAuthJpaRepository memberAuthRepository;

    /** ACTIVE 회원을 새로 가입시키고 소셜 계정을 연동한다. */
    Member signUp(AuthProvider provider, String providerUserId) {
        Member member = memberRepository.save(Member.signUp());
        memberAuthRepository.save(MemberAuth.of(member, provider, providerUserId));

        return member;
    }

    /** 회원의 소셜 연동을 모두 삭제한다. */
    void removeAuth(Long memberId) {
        memberAuthRepository.deleteByMemberId(memberId);
    }

}
