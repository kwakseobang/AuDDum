package org.kwakmunsu.auddum.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.kwakmunsu.auddum.support.error.ErrorCode;

/**
 * OpenAPI 문서에서 사람이 읽는 API 목록(마크다운)을 만든다. 태그(영역)별로 표를 나누고,
 * 각 API가 쓰는 오류 코드와 이 API를 설명하는 기능 페이지 링크를 함께 보인다.
 */
final class ApiListRenderer {

    private static final String API_PREFIX = "/api/v1";
    /** 영역(태그)을 보여 줄 순서. 여기 없는 새 영역은 이름순으로 뒤에 붙는다. */
    private static final List<String> TAG_ORDER = List.of("투표", "참여", "신고", "회원", "인증", "관리자 투표", "관리자 신고", "관리자 회원");
    private static final List<String> METHOD_ORDER = List.of("get", "post", "patch", "put", "delete");
    private static final Pattern FEATURE_API_ROW = Pattern.compile("^\\|\\s*`(GET|POST|PUT|PATCH|DELETE) (/[^`\\s]*)`");

    private ApiListRenderer() {
    }

    record Api(String method, String path, String tag, String summary, String success, List<String> errors) {
    }

    static String render(Map<String, Object> document, Path featuresDirectory, List<ErrorCode> errorCodes) throws IOException {
        Map<String, List<String>> pagesByApi = featurePagesByApi(featuresDirectory);
        Map<String, List<Api>> apisByTag = new TreeMap<>(Comparator.comparingInt((String tag) -> tagRank(tag)).thenComparing(Comparator.naturalOrder()));
        for (Api api : apis(document, errorCodes)) {
            apisByTag.computeIfAbsent(api.tag(), tag -> new ArrayList<>()).add(api);
        }

        String sections = apisByTag.entrySet().stream()
                .map(entry -> section(entry.getKey(), entry.getValue(), pagesByApi))
                .collect(Collectors.joining("\n"));
        int total = apisByTag.values().stream().mapToInt(List::size).sum();

        return """
                <!-- 자동 생성 문서다. 고치지 말고 코드(컨트롤러와 스웨거 어노테이션)를 고친다. -->
                <!-- 다시 만들기: DOCS_UPDATE=1 ./gradlew test --tests '*OpenApiDocTest' -->

                # API 목록

                서버가 제공하는 API %d개를 영역별로 모았다. 경로는 모두 `%s`로 시작한다(표에서는 뺐다). 오류의 뜻과 상태는 [오류 코드](오류-코드.md)에 있고, 요청·응답 필드는 [openapi.json](openapi.json)을 스웨거 뷰어나 타입 생성 도구에 넣어 본다. 기능 칸은 그 API를 쓰는 기능 페이지다.

                카카오 로그인 시작과 콜백은 스프링 시큐리티가 처리하는 경로라 이 목록에 없다.

                %s""".formatted(total, API_PREFIX, sections);
    }

    @SuppressWarnings("unchecked")
    private static List<Api> apis(Map<String, Object> document, List<ErrorCode> errorCodes) {
        List<Api> apis = new ArrayList<>();
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        for (var path : paths.entrySet()) {
            for (var method : ((Map<String, Object>) path.getValue()).entrySet()) {
                Map<String, Object> operation = (Map<String, Object>) method.getValue();
                Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
                String tag = ((List<String>) operation.get("tags")).getFirst();
                String shortPath = path.getKey().startsWith(API_PREFIX) ? path.getKey().substring(API_PREFIX.length()) : path.getKey();
                apis.add(new Api(method.getKey().toUpperCase(), shortPath, tag, (String) operation.get("summary"),
                        success(responses), errors(responses, errorCodes)));
            }
        }
        apis.sort(Comparator.comparing(Api::path).thenComparing(api -> METHOD_ORDER.indexOf(api.method().toLowerCase())));

        return apis;
    }

    private static int tagRank(String tag) {
        int rank = TAG_ORDER.indexOf(tag);

        return rank >= 0 ? rank : TAG_ORDER.size();
    }

    private static String success(Map<String, Object> responses) {
        return responses.keySet().stream().filter(status -> status.startsWith("2")).sorted().findFirst().orElse("-");
    }

    /** 4xx·5xx 응답 설명에 적힌 오류 코드를 적힌 순서대로 모은다. 코드 이름은 오류 코드 enum에 있는 것만 인정한다. */
    @SuppressWarnings("unchecked")
    private static List<String> errors(Map<String, Object> responses, List<ErrorCode> errorCodes) {
        Set<String> found = new LinkedHashSet<>();
        responses.entrySet().stream()
                .filter(response -> response.getKey().compareTo("400") >= 0)
                .sorted(Map.Entry.comparingByKey())
                .forEach(response -> {
                    String description = String.valueOf(((Map<String, Object>) response.getValue()).get("description"));
                    errorCodes.stream()
                            .map(ErrorCode::name)
                            .map(name -> Map.entry(name, position(description, name)))
                            .filter(entry -> entry.getValue() >= 0)
                            .sorted(Map.Entry.comparingByValue())
                            .forEach(entry -> found.add(entry.getKey()));
                });

        return List.copyOf(found);
    }

    private static int position(String description, String name) {
        Matcher matcher = Pattern.compile("(?<![A-Z0-9_])" + Pattern.quote(name) + "(?![A-Z0-9_])").matcher(description);

        return matcher.find() ? matcher.start() : -1;
    }

    private static String section(String tag, List<Api> apis, Map<String, List<String>> pagesByApi) {
        String rows = apis.stream()
                .map(api -> "| `%s %s` | %s | %s | %s | %s |".formatted(
                        api.method(), api.path(), api.summary(), api.success(),
                        api.errors().isEmpty() ? "-" : api.errors().stream().map(code -> "`" + code + "`").collect(Collectors.joining(", ")),
                        links(pagesByApi.getOrDefault(api.method() + " " + api.path(), List.of()))))
                .collect(Collectors.joining("\n"));

        return """
                ## %s

                | API | 하는 일 | 성공 | 오류 | 기능 |
                | --- | --- | --- | --- | --- |
                %s
                """.formatted(tag, rows);
    }

    private static String links(List<String> pages) {
        return pages.isEmpty() ? "-" : pages.stream().map(page -> "[%s](../features/%s.md)".formatted(page, page)).collect(Collectors.joining(", "));
    }

    /** 기능 페이지의 '사용하는 API' 표에서 어느 페이지가 어느 API를 쓰는지 읽는다. */
    private static Map<String, List<String>> featurePagesByApi(Path featuresDirectory) throws IOException {
        Map<String, List<String>> pagesByApi = new TreeMap<>();
        try (Stream<Path> files = Files.list(featuresDirectory)) {
            for (Path page : files.filter(file -> file.getFileName().toString().matches("\\d{2}-.+\\.md")).sorted().toList()) {
                String name = page.getFileName().toString().replaceFirst("\\.md$", "");
                for (String line : Files.readAllLines(page)) {
                    Matcher row = FEATURE_API_ROW.matcher(line);
                    if (row.find()) {
                        pagesByApi.computeIfAbsent(row.group(1) + " " + row.group(2), api -> new ArrayList<>()).add(name);
                    }
                }
            }
        }

        return pagesByApi;
    }

}
