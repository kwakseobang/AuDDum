package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 테스트-배포: 허용 Origin 목록은 설정값(쉼표 구분)에서 오므로, 공백과 빈 항목이 섞여 있어도 정리돼 해당 Origin이 허용된다.
 */
@TestPropertySource(properties = {
    "auddum.frontend.base-url=https://auddum.example.com",
    "auddum.cors.allowed-origins= https://www.auddum.example.com , "
})
@AutoConfigureMockMvc
@RequiredArgsConstructor
class CorsAllowlistBlankEntriesTest extends IntegrationTestSupport {

    final MockMvcTester mvcTester;

    @Test
    void 공백과_빈_항목이_섞인_목록에서도_해당_Origin의_사전_요청이_허용된다() {
        var result = mvcTester.options().uri("/api/v1/polls")
            .header("Origin", "https://www.auddum.example.com")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type")
            .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).headers().hasValue("Access-Control-Allow-Origin", "https://www.auddum.example.com");
        assertThat(result).headers().hasValue("Access-Control-Allow-Credentials", "true");
    }

}
