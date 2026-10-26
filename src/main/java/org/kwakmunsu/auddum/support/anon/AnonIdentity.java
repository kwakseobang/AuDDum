package org.kwakmunsu.auddum.support.anon;

import java.util.UUID;

/**
 * 비회원 요청의 익명 식별자. 쿠키를 새로 발급했을 때만 {@code newCookieValue}가 채워지고, 아니면 null이다.
 * 이 식별자를 참여자(Voter)나 신고자(Reporter)로 감싸는 일은 각 도메인이 한다.
 */
public record AnonIdentity(UUID id, String newCookieValue) {

    /** 이미 정해진 식별자(유효한 쿠키). 쿠키를 새로 발급하지 않았다. */
    public static AnonIdentity existing(UUID id) {
        return new AnonIdentity(id, null);
    }

    /** 새로 발급한 쿠키의 식별자. */
    public static AnonIdentity issued(AnonCookie cookie) {
        return new AnonIdentity(cookie.id(), cookie.value());
    }

}
