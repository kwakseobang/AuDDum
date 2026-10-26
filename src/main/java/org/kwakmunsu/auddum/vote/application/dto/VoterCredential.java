package org.kwakmunsu.auddum.vote.application.dto;

/**
 * 참여하려는 사람이 요청에 담아 보낸 신원 단서. 아직 확인 전이며, 참여자(Voter)로 확정하는 일은 VoterResolver가 한다.
 *
 * @param memberId        로그인한 회원 ID. 비회원이면 null
 * @param anonCookieValue {@code auddum_anon} 쿠키 값. 없으면 null
 */
public record VoterCredential(Long memberId, String anonCookieValue) {

    /** 로그인한 회원인가. 회원이면 익명 쿠키는 보지 않는다. */
    public boolean isMember() {
        return memberId != null;
    }

}
