package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 결정(사용자 승인): {@code auddum.cors.allowed-origins}는 선택이며 비어 있어도 된다.
 * 비어 있으면 허용 Origin은 {@code auddum.frontend.base-url}의 origin뿐이다. 와일드카드는 쓰지 않는다(테스트-배포 §3).
 */
@TestPropertySource(properties = {
    "auddum.frontend.base-url=https://auddum.example.com",
    "auddum.cors.allowed-origins="
})
@AutoConfigureMockMvc
@RequiredArgsConstructor
class CorsBaseUrlOnlyChainTest extends IntegrationTestSupport {

    static final String BASE_ORIGIN = "https://auddum.example.com";
    static final String OTHER_ORIGIN = "https://www.auddum.example.com";

    final MockMvcTester mvcTester;

    @Test
    void 추가_목록이_비어_있으면_base_url의_Origin만_사전_요청을_허용한다() {
        var allowed = preflight(BASE_ORIGIN);
        var denied = preflight(OTHER_ORIGIN);

        assertThat(allowed).hasStatusOk();
        assertThat(allowed).headers().hasValue("Access-Control-Allow-Origin", BASE_ORIGIN);
        assertThat(allowed).headers().hasValue("Access-Control-Allow-Credentials", "true");
        assertThat(denied).hasStatus(403);
        assertThat(denied.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult preflight(String origin) {
        return mvcTester.options().uri("/api/v1/polls")
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type")
            .exchange();
    }

}
