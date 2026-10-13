package org.kwakmunsu.auddum;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * 서비스 통합 테스트의 공통 설정. 실제 빈과 H2를 쓰며, 같은 설정이라 스프링 컨텍스트를 테스트끼리 공유한다.
 * 하위 클래스는 {@code @RequiredArgsConstructor}와 final 필드로 생성자 주입을 받는다.
 */
@SpringBootTest
@TestConstructor(autowireMode = AutowireMode.ALL)
public abstract class IntegrationTestSupport {

}
