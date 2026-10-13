package org.kwakmunsu.auddum.support.stereotype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Service;

/**
 * {@code application} 계층의 서비스. 도메인 객체에게 일을 시키고 흐름을 조합한다.
 * 쓰기와 읽기는 {@link CommandService}, {@link QueryService}로 나눠 쓴다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface ApplicationService {

}
