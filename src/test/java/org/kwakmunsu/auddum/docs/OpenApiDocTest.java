package org.kwakmunsu.auddum.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 생성 문서(docs/generated/openapi.json)가 코드의 OpenAPI 문서와 같은지 검사한다.
 * 다르면 실패한다. 환경 변수 DOCS_UPDATE=1로 돌리면 파일을 다시 쓴다.
 * 예: DOCS_UPDATE=1 ./gradlew test --tests '*OpenApiDocTest'
 */
@AutoConfigureMockMvc
@RequiredArgsConstructor
class OpenApiDocTest extends IntegrationTestSupport {

    private static final Path TARGET = Path.of("docs/generated/openapi.json");

    private static final JsonMapper sortedMapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .build();

    final MockMvcTester mvcTester;
    final JsonMapper jsonMapper;

    @Test
    void OpenAPI_문서가_코드와_같다() throws IOException {
        String expected = render();

        if ("1".equals(System.getenv("DOCS_UPDATE"))) {
            Files.writeString(TARGET, expected, StandardCharsets.UTF_8);
        }

        assertThat(TARGET).as("DOCS_UPDATE=1 ./gradlew test --tests '*OpenApiDocTest' 로 다시 만든다").hasContent(expected);
    }

    /** 사람이 diff로 읽도록 들여쓰고 키 순서를 고정한다. 서버 주소는 실행 환경마다 달라 뺀다. */
    private String render() throws IOException {
        String body = mvcTester.get().uri("/v3/api-docs").exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> document = jsonMapper.readValue(body, Map.class);
        document.remove("servers");

        return sortedMapper.writeValueAsString(document) + "\n";
    }

}
