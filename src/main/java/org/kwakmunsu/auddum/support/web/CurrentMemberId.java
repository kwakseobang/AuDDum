package org.kwakmunsu.auddum.support.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 회원의 ID를 컨트롤러 파라미터로 받는다. 액세스 토큰으로 확인된 신원에서 꺼내며, 로그인하지 않았으면 401이다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentMemberId {

}
