package org.kwakmunsu.auddum.member.application;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.stereotype.QueryService;

@RequiredArgsConstructor
@QueryService
public class MemberQueryService {

    private final MemberJpaRepository memberRepository;

    public Optional<Member> findMember(Long memberId) {
        return memberRepository.findById(memberId);
    }

}
