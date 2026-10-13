package org.kwakmunsu.auddum.support.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청한 회원의 ID를 컨트롤러 파라미터로 받는다. 인증(M3, #13) 전까지는 임시로 X-Member-Id 헤더에서 읽고,
 * 인증이 들어오면 {@link CurrentMemberIdResolver}만 바꾼다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentMemberId {

}
