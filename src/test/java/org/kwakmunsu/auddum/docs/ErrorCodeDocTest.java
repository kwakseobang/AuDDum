package org.kwakmunsu.auddum.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.ErrorCode;

/**
 * 생성 문서(docs/generated/오류-코드.md)가 코드와 같은지 검사한다.
 * 다르면 실패한다. 환경 변수 DOCS_UPDATE=1로 돌리면 파일을 다시 쓴다.
 * 예: DOCS_UPDATE=1 ./gradlew test --tests '*ErrorCodeDocTest'
 */
class ErrorCodeDocTest {

    private static final String ROOT = "org.kwakmunsu.auddum";

    /** 절의 순서와 제목. 공통 코드는 support 패키지에 있다. */
    private static final Map<String, String> DOMAINS = new LinkedHashMap<>(Map.of());

    static {
        DOMAINS.put("support", "공통");
        DOMAINS.put("member", "회원");
        DOMAINS.put("poll", "투표");
        DOMAINS.put("vote", "참여");
        DOMAINS.put("report", "신고");
    }

    private static final Path TARGET = Path.of("docs/generated/오류-코드.md");

    @Test
    void 오류_코드_표가_코드와_같다() throws IOException {
        String expected = render(errorCodes());

        if ("1".equals(System.getenv("DOCS_UPDATE"))) {
            Files.writeString(TARGET, expected);
        }

        assertThat(TARGET).as("DOCS_UPDATE=1 ./gradlew test --tests '*ErrorCodeDocTest' 로 다시 만든다").hasContent(expected);
    }

    @Test
    void 오류_코드_이름은_모든_enum에서_겹치지_않는다() {
        List<String> names = errorCodes().stream().map(ErrorCode::name).toList();

        assertThat(names).doesNotHaveDuplicates();
    }

    /** ErrorCode를 구현한 enum을 모두 찾는다. 새 enum을 더해도 목록을 손으로 고치지 않는다. */
    static List<ErrorCode> errorCodes() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        return classes.stream()
                .filter(c -> c.isEnum() && c.isAssignableTo(ErrorCode.class))
                .map(JavaClass::reflect)
                .flatMap(c -> Arrays.stream(c.getEnumConstants()))
                .map(ErrorCode.class::cast)
                .sorted(Comparator.comparing((ErrorCode c) -> c.getStatus().value()).thenComparing(ErrorCode::name))
                .toList();
    }

    private static String render(List<ErrorCode> codes) {
        String sections = DOMAINS.entrySet().stream()
                .map(domain -> section(domain.getValue(), codes.stream().filter(c -> domainOf(c).equals(domain.getKey())).toList()))
                .filter(section -> !section.isEmpty())
                .collect(Collectors.joining("\n"));

        return """
                <!-- 자동 생성 문서다. 고치지 말고 코드(ErrorCode를 구현한 enum)를 고친다. -->
                <!-- 다시 만들기: DOCS_UPDATE=1 ./gradlew test --tests '*ErrorCodeDocTest' -->

                # 오류 코드

                서버가 JSON으로 돌려주는 오류 코드 %d개를 도메인별로 모았다. 응답의 `code`는 이 표의 코드이고, 상태는 HTTP 응답 코드다. 4로 시작하면 요청이 잘못되었거나 지금은 받을 수 없다는 뜻이고, 5로 시작하면 서버 문제다. 메시지는 사용자에게 그대로 보여 줘도 되는 문장이다. 로그 수준은 서버가 이 오류를 기록하는 중요도다.

                %s""".formatted(codes.size(), sections);
    }

    private static String section(String title, List<ErrorCode> codes) {
        if (codes.isEmpty()) {
            return "";
        }
        String rows = codes.stream()
                .map(c -> "| `%s` | %d | %s | %s |".formatted(c.name(), c.getStatus().value(), c.getMessage(), c.getLogLevel()))
                .collect(Collectors.joining("\n"));

        return """
                ## %s

                | 코드 | 상태 | 메시지 | 로그 수준 |
                | --- | --- | --- | --- |
                %s
                """.formatted(title, rows);
    }

    /** enum이 속한 도메인 패키지(org.kwakmunsu.auddum.<도메인>...)의 이름. 새 도메인이 생기면 DOMAINS에 더해야 한다. */
    private static String domainOf(ErrorCode code) {
        String packageName = code.getClass().getPackageName();
        String domain = packageName.substring(ROOT.length() + 1).split("\\.")[0];
        assertThat(DOMAINS).as("%s의 도메인을 DOMAINS에 더한다", packageName).containsKey(domain);
        return domain;
    }


}
