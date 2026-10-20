package org.kwakmunsu.auddum.vote.application;

import org.kwakmunsu.auddum.vote.domain.Voter;

/** 결정된 참여자. 쿠키를 새로 발급했을 때만 {@code newCookieValue}가 채워지고, 아니면 null이다. */
public record ResolvedVoter(Voter voter, String newCookieValue) {

    /** 이미 정해진 참여자(회원, 유효한 쿠키). 쿠키를 새로 발급하지 않았다. */
    public static ResolvedVoter existing(Voter voter) {
        return new ResolvedVoter(voter, null);
    }

    /** 새로 발급한 쿠키의 참여자. */
    public static ResolvedVoter issued(Voter voter, String newCookieValue) {
        return new ResolvedVoter(voter, newCookieValue);
    }

}
