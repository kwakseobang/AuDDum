package org.kwakmunsu.auddum.vote.application;

import org.kwakmunsu.auddum.vote.domain.Voter;

/** 결정된 참여자. 쿠키를 새로 발급했을 때만 {@code newCookieValue}가 채워지고, 아니면 null이다. */
public record ResolvedVoter(Voter voter, String newCookieValue) {

}
