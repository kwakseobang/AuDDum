package org.kwakmunsu.auddum.support.web;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 익명 식별 쿠키의 이름. 컨트롤러가 쿠키를 읽을 때 쓰는 이름이다. 발급·검증은 {@code support.anon}, 속성은 {@link AnonCookies}가 맡는다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnonCookieName {

    public static final String VALUE = "auddum_anon";

}
