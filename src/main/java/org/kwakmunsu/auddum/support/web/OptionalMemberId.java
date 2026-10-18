package org.kwakmunsu.auddum.support.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인했으면 회원 ID를, 아니면 null을 컨트롤러 파라미터로 받는다. 비회원도 열 수 있되 회원이면 달라지는 조회에 쓴다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OptionalMemberId {

}
