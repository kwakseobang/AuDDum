package org.kwakmunsu.auddum.member.application;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.domain.AuthProvider;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.MemberAuth;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.stereotype.Component;

/**
 * 서비스가 요청한 회원을 가져오고 상태를 확인하는 공통 조회. 비즈니스 흐름이 아니라 검증을 위한 조회라 서비스가 아니다.
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 그대로 참여하므로, 예외를 던져도 그 트랜잭션을 롤백 전용으로 만들지 않는다.
 * 다른 도메인은 이 클래스로 회원을 확인한다.
 */
@RequiredArgsConstructor
@Component
public class MemberReader {

    private final MemberJpaRepository memberRepository;
    private final MemberAuthJpaRepository memberAuthRepository;

    /** 요청한 회원을 가져온다. 없는 회원이면 401이다. */
    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
            .orElseThrow(() -> new AuddumException(DefaultErrorCode.UNAUTHENTICATED));
    }

    /** 탈퇴하지 않은 회원을 가져온다. 없거나 탈퇴했으면 401이다. 정지 회원은 통과한다(정지 중에도 재발급·조회·탈퇴는 할 수 있다). */
    public Member getNotWithdrawnMember(Long memberId) {
        Member member = getMember(memberId);
        member.validateNotWithdrawn();

        return member;
    }

    /** 쓰기를 할 수 있는 정상 회원만 가져온다. 없거나 탈퇴했으면 401, 정지면 403이다. 정지 회원이 할 수 있는 탈퇴·재발급에서는 {@link #getMember}를 쓴다. */
    public Member getActiveMember(Long memberId) {
        Member member = getMember(memberId);
        member.validateActive();

        return member;
    }

    /** 관리자인지 확인한다. 권한은 토큰이 아니라 DB의 role로 판단하고, 비회원·없는 회원·탈퇴한 회원·정지된 회원은 false다. */
    public boolean isAdmin(Long memberId) {
        if (memberId == null) {
            return false;
        }

        return memberRepository.findById(memberId)
            .map(Member::hasAdminAuthority)
            .orElse(false);
    }

    /** 소셜 계정에 연동된 회원. 연동된 회원이 없으면(처음이거나 탈퇴한 계정) 비어 있다. */
    Optional<Member> findByAuth(AuthProvider provider, String providerUserId) {
        return memberAuthRepository.findByProviderAndProviderUserId(provider, providerUserId)
            .map(MemberAuth::getMember);
    }

}
