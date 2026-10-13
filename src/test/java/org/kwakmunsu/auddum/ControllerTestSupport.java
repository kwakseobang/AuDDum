package org.kwakmunsu.auddum;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * 컨트롤러(웹 계층) 테스트의 공통 설정. 서비스는 하위 클래스에서 {@code @MockitoBean}으로 대체한다.
 */
@WebMvcTest
public abstract class ControllerTestSupport {

    @Autowired
    protected MockMvcTester mvcTester;

    @Autowired
    protected JsonMapper jsonMapper;

}
