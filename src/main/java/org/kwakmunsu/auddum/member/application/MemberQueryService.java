package org.kwakmunsu.auddum.member.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.support.stereotype.QueryService;

@RequiredArgsConstructor
@QueryService
public class MemberQueryService {

    private final MemberReader memberReader;

    /** 요청한 회원의 정보를 가져온다. 정지 회원도 자기 상태를 볼 수 있다. 없거나 탈퇴한 회원이면 401이다. */
    public Member getMyInfo(Long memberId) {
        return memberReader.getNotWithdrawnMember(memberId);
    }

}
