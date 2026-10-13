package org.kwakmunsu.auddum;

import org.kwakmunsu.auddum.poll.application.PollCommandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * 컨트롤러(웹 계층) 테스트의 공통 설정. 컨트롤러가 의존하는 서비스는 여기서 {@code @MockitoBean}으로 대체한다.
 */
@WebMvcTest
public abstract class WebApiTestSupport {

    @Autowired
    protected MockMvcTester mvcTester;

    @Autowired
    protected JsonMapper jsonMapper;

    @MockitoBean
    protected PollCommandService pollCommandService;

}
