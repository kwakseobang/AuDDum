package org.kwakmunsu.auddum.support.web;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 익명 식별 쿠키의 이름. 컨트롤러가 쿠키를 읽을 때 쓰는 이름만 여기 두고, 발급·검증·속성은 vote가 맡는다(인증-중복-참여 §2). */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnonCookieName {

    public static final String VALUE = "auddum_anon";

}
