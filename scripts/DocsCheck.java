import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 문서와 코드 주석의 ID(P-xx, OP-xx, UC-xx, R-애그리거트-xx)를 검사한다.
 *
 * <p>정의는 정해진 파일에서만 모은다. 정의가 없는 참조, 중복 정의, 형식 오류, 도메인-모델 밖의 R-ID 정의는 실패다.
 * 기능 페이지(docs/features)의 P·OP·UC 정의도 모으고, sketch(docs/records/sketches)의 머리말과 기능 페이지의 이력 링크가 서로를 가리키는지 본다.
 * docs의 상대 링크가 가리키는 파일이 없으면 실패다. 옮기거나 없앤 문서의 이름(퇴역 이름)을 docs·.claude·CLAUDE.md에서 쓰면 실패다(시점 기록인 docs/records는 제외).
 * src의 문서 이름 언급은 실패다. 절 번호(§) 참조는 docs와 src에서는 실패이고, .claude와 CLAUDE.md는 개수만 보고한다(하네스는 1단계에서 정리한다).
 * docs의 가독성(긴 문장, #### 제목, 줄 전체 굵은 글씨, 알림 상자 초과, 굵은 글씨가 많은 절)은 개수만 보고한다. 기준은 docs/index.md의 표기 규칙이다.
 *
 * <p>사용법: {@code scripts/docs-check [--root <디렉터리>] [--readability]} (저장소 루트에서 실행, --readability는 가독성 위치를 모두 보인다)
 */
public class DocsCheck {

    static final String AGGREGATES = "MEMBER|POLL|VOTE|REPORT|ACTION";

    // 정의 파일. 문서를 기능 단위로 다시 나눌 때 이 경로를 함께 고친다.
    static final String DOMAIN_FILE = "docs/domain/도메인-모델.md";

    // 기능 페이지(01-둘러보기.md처럼 번호로 시작)와 sketch(이슈번호로 시작)만 검사한다. 양식(_로 시작)과 README는 건너뛴다.
    static final String FEATURES_DIR = "docs/features";
    static final String NO_FEATURE = "없음";
    static final String SKETCH_DIR = "docs/records/sketches";
    static final Pattern FEATURE_PAGE_NAME = Pattern.compile("\\d{2}-.+\\.md");
    static final Pattern SKETCH_NAME = Pattern.compile("\\d+-.+\\.md");
    static final Pattern SKETCH_LINK = Pattern.compile("records/sketches/([^)\\s#]+\\.md)");

    // 표의 첫 칸이 ID로 시작하는 행이 정의다. (P-28처럼 아직 정하지 않은 항목도 이 형태로 정의한다)
    static final Pattern ROW_DEFINITION = Pattern.compile("^\\|\\s*((?:P|OP)-\\d{2})(?![A-Za-z0-9])");
    // 기능 페이지에서는 UC도 표의 첫 칸으로 정의할 수 있다.
    static final Pattern UC_ROW_DEFINITION = Pattern.compile("^\\|\\s*(UC-\\d{2})(?![A-Za-z0-9])");
    static final Pattern HEADING_DEFINITION = Pattern.compile("^#{3}\\s+(UC-\\d{2})(?![A-Za-z0-9])");
    static final Pattern BOLD_RULE_DEFINITION = Pattern.compile("\\*\\*(R-(?:" + AGGREGATES + ")-\\d{2})\\*\\*");

    static final Pattern VALID_ID_FULL = Pattern.compile("(?:P|OP|UC)-\\d{2}|R-(?:" + AGGREGATES + ")-\\d{2}");
    static final Pattern VALID_ID = Pattern.compile(
        "(?<![A-Za-z0-9_-])((?:P|OP|UC)-\\d{2}|R-(?:" + AGGREGATES + ")-\\d{2})(?![A-Za-z0-9])");
    static final Pattern ID_LIKE = Pattern.compile(
        "(?<![A-Za-z0-9_-])((?:P|OP|UC)-\\d+|R-[A-Za-z]+-\\d+)(?![A-Za-z0-9])");

    static final Pattern CODE_SPAN = Pattern.compile("`[^`]*`");
    static final Pattern SECTION_REF = Pattern.compile("§\\s?\\d");

    // 코드 주석은 문서 이름을 쓰지 않는다(문서를 나누거나 이름을 바꾸면 조용히 낡는다). 근거는 ID나 코드 식별자로 단다.
    // docs/generated는 코드가 만드는 문서라 생성기가 경로를 알아야 하므로 예외다.
    static final Pattern DOC_NAME = Pattern.compile(
        "API-명세|인증-중복-참여|도메인-모델|예외-정책|설계-결정|사용자-시나리오|테스트-배포|개발-가이드"
            + "|예외-응답|동시성-중복-방지|테스트-전략|개발-워크플로|로컬-환경"
            + "|아키텍처 설계서|(?<![A-Za-z0-9_])ERD(?![A-Za-z0-9_])|docs/(?!generated/)\\S+\\.md");
    static final Set<String> DOC_NAME_FAIL_AREAS = Set.of("src");

    // 옮기거나 없앤 문서의 이름. 다시 쓰면 낡은 안내가 되므로 새 이름으로 바꾸게 한다. 시점 기록(docs/records)은 검사하지 않는다.
    static final Map<String, String> RETIRED_NAMES = retiredNames();
    static final String RECORDS_DIR = "docs/records/";
    static final Set<String> RETIRED_NAME_AREAS = Set.of("docs", ".claude", "CLAUDE.md");
    static final Pattern MD_LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");

    // 기능 페이지가 적은 사실(오류 코드와 상태, 테스트 클래스 이름)이 코드와 같은지 본다.
    // 아직 구현하지 않은 것은 같은 줄에 "구현 전"이라고 적으면 넘어간다.
    static final Pattern ERROR_CODE_ENTRY = Pattern.compile("^\\s+([A-Z][A-Z0-9_]+)\\s*\\(HttpStatus\\.([A-Z_]+)");
    static final Pattern STATUS_AND_CODE = Pattern.compile("(\\d{3})\\s*`([A-Z][A-Z0-9_]+)`");
    static final Pattern CODE_NAME = Pattern.compile("`([A-Z]+(?:_[A-Z]+)+)`");
    static final Pattern TEST_NAME = Pattern.compile("`([A-Z][A-Za-z0-9]*Test)`");
    static final String NOT_YET = "구현 전";
    static final Map<String, Integer> HTTP_STATUS = Map.of(
        "BAD_REQUEST", 400, "UNAUTHORIZED", 401, "FORBIDDEN", 403, "NOT_FOUND", 404,
        "CONFLICT", 409, "TOO_MANY_REQUESTS", 429, "INTERNAL_SERVER_ERROR", 500, "BAD_GATEWAY", 502);

    // 기능 페이지의 "사용하는 API"가 코드의 OpenAPI 문서(docs/generated/openapi.json)와 맞는지 본다.
    // 컨트롤러가 아니라 필터가 처리하는 카카오 로그인 경로는 OpenAPI 문서에 없어서 따로 허용한다.
    static final String OPENAPI_FILE = "docs/generated/openapi.json";
    static final String API_PREFIX = "/api/v1";
    static final Pattern OPENAPI_PATH = Pattern.compile("^ {4}\"(/[^\"]*)\" : \\{");
    static final Pattern OPENAPI_METHOD = Pattern.compile("^ {6}\"(get|post|put|patch|delete)\" : \\{");
    static final Pattern API_ROW = Pattern.compile("^\\|\\s*`(GET|POST|PUT|PATCH|DELETE) (/[^`\\s]*)`");
    static final Set<String> FILTER_PROVIDED_APIS = Set.of("GET /oauth2/authorization/kakao", "GET /login/oauth2/code/kakao");

    static final Set<String> SKIPPED_DIRS = Set.of("_workspace", "build", ".git", ".gradle", "node_modules", "out");
    static final List<String> AREAS = List.of("docs", "src", ".claude", "CLAUDE.md");
    static final Set<String> SECTION_REF_FAIL_AREAS = Set.of("docs", "src");

    // 가독성은 기계 기준이라 틀리게 잡을 수 있어 실패시키지 않고 보고만 한다. 시점 기록(records)과 생성 문서(generated)는 보지 않는다.
    static final String GENERATED_DIR = "docs/generated/";
    static final int LONG_SENTENCE = 100;
    static final int MAX_ALERTS = 2;
    static final int MAX_INLINE_BOLDS = 1;
    static final String LONG = "긴 문장";
    static final String DEEP_HEADING = "#### 제목";
    static final String BOLD_LINE = "줄 전체 굵은 글씨";
    static final String TOO_MANY_ALERTS = "알림 상자 초과";
    static final String BOLD_HEAVY = "굵은 글씨가 많은 절";
    static final Pattern ALERT = Pattern.compile("^>\\s*\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]");
    static final Pattern BOLD_ONLY_LINE = Pattern.compile("^\\*\\*[^*]+\\*\\*(\\([^)]*\\))?(\\s+—\\s+.{1,40})?:?$");
    static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    static final Pattern LIST_LABEL = Pattern.compile("^\\s*(?:[-*]|\\d+\\.|>)\\s*\\*\\*[^*]+\\*\\*");
    static final Pattern CELL_LABEL = Pattern.compile("\\|\\s*\\*\\*[^*]+\\*\\*");
    static final Pattern LINK_TARGET = Pattern.compile("\\]\\([^)]*\\)");
    static final Pattern SENTENCE_END = Pattern.compile("(?<=[.?!])\\s+");

    // 이미 적용된 Flyway 마이그레이션은 주석 한 줄만 바꿔도 체크섬이 달라져 검증이 실패하므로 검사하지 않는다.
    static final String MIGRATION_DIR = "src/main/resources/db/migration/";

    private static Map<String, String> retiredNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("예외-정책", "features/ 기능 페이지");
        names.put("사용자-시나리오", "features/ 기능 페이지");
        names.put("인증-중복-참여", "인증, 동시성-중복-방지");
        names.put("개발-가이드", "컨벤션, 개발-워크플로, 로컬-환경");
        names.put("테스트-배포", "테스트-전략, 배포");
        names.put("docs/README.md", "docs/index.md");
        names.put("사용자 시나리오 명세서", "features/ 기능 페이지");
        names.put("예외·정책 명세서", "features/ 기능 페이지");
        names.put("예외-응답", "features/ 기능 페이지, generated/오류-코드");
        names.put("API-명세", "engineering/API-공통-규칙, generated/API-목록");
        names.put("인증·중복 참여 설계서", "인증, 동시성-중복-방지");
        names.put("테스트·배포 설계서", "테스트-전략, 배포");
        return names;
    }

    record Result(Map<String, String> definitions, List<String> errors, Map<String, Integer> sectionRefs, List<Readability> readability) {
    }

    record Readability(String kind, String location) {
    }

    public static void main(String[] args) {
        Path root = Path.of(".");
        boolean showReadability = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--root") && i + 1 < args.length) {
                root = Path.of(args[++i]);
            } else if (args[i].equals("--readability")) {
                showReadability = true;
            }
        }
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

        Result result = check(root.toAbsolutePath().normalize());

        out.println("docs-check: 정의 " + countByKind(result.definitions()));
        if (result.errors().isEmpty()) {
            out.println("오류 없음");
        } else {
            out.println("오류 " + result.errors().size() + "건");
            result.errors().forEach(e -> out.println("  " + e));
        }
        out.println("절 번호(§) 참조 개수(docs·src는 실패, 나머지는 보고만): " + result.sectionRefs());
        Map<String, Integer> byKind = new LinkedHashMap<>();
        List.of(LONG, DEEP_HEADING, BOLD_LINE, TOO_MANY_ALERTS, BOLD_HEAVY).forEach(kind -> byKind.put(kind, 0));
        result.readability().forEach(r -> byKind.merge(r.kind(), 1, Integer::sum));
        out.println("가독성(보고만, 위치는 --readability): " + byKind);
        if (showReadability) {
            result.readability().forEach(r -> out.println("  " + r.location() + "  " + r.kind()));
        }

        System.exit(result.errors().isEmpty() ? 0 : 1);
    }

    static Result check(Path root) {
        List<String> errors = new ArrayList<>();
        Map<String, String> definitions = new TreeMap<>();
        collectDefinitions(root, definitions, errors);

        Map<String, Integer> sectionRefs = new LinkedHashMap<>();
        AREAS.forEach(area -> sectionRefs.put(area, 0));
        List<Readability> readability = new ArrayList<>();

        for (Path file : scanFiles(root)) {
            String relative = relativePath(root, file);
            String area = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : relative;
            List<String> lines = readLines(file);
            if (area.equals("docs") && relative.endsWith(".md") && !relative.startsWith(RECORDS_DIR)
                && !relative.startsWith(GENERATED_DIR) && !file.getFileName().toString().startsWith("_")) {
                checkReadability(lines, relative, readability);
            }
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String location = relative + ":" + (i + 1);
                checkLine(line, location, relative, definitions, errors);

                Matcher docName = DOC_NAME.matcher(line);
                if (docName.find() && DOC_NAME_FAIL_AREAS.contains(area)) {
                    errors.add(location + "  문서 이름 언급: " + docName.group() + " (정책·규칙·시나리오 ID나 코드 식별자로 바꾸거나 지운다)");
                }

                if (RETIRED_NAME_AREAS.contains(area) && !relative.startsWith(RECORDS_DIR)) {
                    for (var retired : RETIRED_NAMES.entrySet()) {
                        if (line.contains(retired.getKey())) {
                            errors.add(location + "  퇴역한 문서 이름: " + retired.getKey() + " (새 이름: " + retired.getValue() + ")");
                        }
                    }
                }
                if (area.equals("docs") && relative.endsWith(".md")
                    && !file.getFileName().toString().startsWith("_")) {
                    checkMarkdownLinks(file, line, location, errors);
                }

                int sections = countMatches(SECTION_REF, line);
                sectionRefs.merge(area, sections, Integer::sum);
                if (sections > 0 && SECTION_REF_FAIL_AREAS.contains(area)) {
                    errors.add(location + "  절 번호 참조: " + sectionToken(line) + " (정책·규칙·시나리오 ID로 바꾸거나 지운다)");
                }
            }
        }
        checkSketchLinks(root, errors);
        checkFeatureFacts(root, errors);
        checkFeatureApis(root, errors);
        return new Result(definitions, errors, sectionRefs, readability);
    }

    // docs/index.md 표기 규칙의 기계로 셀 수 있는 부분만 센다. 코드 블록은 보지 않는다.
    private static void checkReadability(List<String> lines, String relative, List<Readability> found) {
        boolean inCode = false;
        int alerts = 0;
        int sectionStart = 1;
        int sectionBolds = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String location = relative + ":" + (i + 1);
            if (line.strip().startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                continue;
            }
            if (line.startsWith("#")) {
                if (sectionBolds > MAX_INLINE_BOLDS) {
                    found.add(new Readability(BOLD_HEAVY, relative + ":" + sectionStart + " (" + sectionBolds + "개)"));
                }
                sectionStart = i + 1;
                sectionBolds = 0;
                if (line.startsWith("####")) {
                    found.add(new Readability(DEEP_HEADING, location));
                }
                continue;
            }
            String text = CODE_SPAN.matcher(line).replaceAll("`x`");
            if (BOLD_ONLY_LINE.matcher(text.strip()).matches()) {
                found.add(new Readability(BOLD_LINE, location));
                continue;
            }
            if (ALERT.matcher(line).find()) {
                alerts++;
            }
            sectionBolds += countInlineBolds(text);
            for (String sentence : sentences(text)) {
                if (sentence.codePointCount(0, sentence.length()) > LONG_SENTENCE) {
                    found.add(new Readability(LONG, location));
                    break;
                }
            }
        }
        if (sectionBolds > MAX_INLINE_BOLDS) {
            found.add(new Readability(BOLD_HEAVY, relative + ":" + sectionStart + " (" + sectionBolds + "개)"));
        }
        if (alerts > MAX_ALERTS) {
            found.add(new Readability(TOO_MANY_ALERTS, relative + " (" + alerts + "개)"));
        }
    }

    // 목록·표 칸의 머리 라벨, R-ID 정의, 숫자 강조는 규칙이 허용하므로 세지 않는다.
    private static int countInlineBolds(String text) {
        String rest = LIST_LABEL.matcher(text).replaceFirst("");
        if (rest.startsWith("|")) {
            rest = CELL_LABEL.matcher(rest).replaceAll("|");
        }
        int count = 0;
        Matcher bold = BOLD.matcher(rest);
        while (bold.find()) {
            String inner = bold.group(1);
            if (VALID_ID_FULL.matcher(inner).matches() || inner.chars().anyMatch(Character::isDigit)) {
                continue;
            }
            count++;
        }
        return count;
    }

    // 표는 칸마다, 나머지는 줄을 문장으로 나눈다. 링크 주소와 서식 기호는 길이에 넣지 않는다.
    private static List<String> sentences(String line) {
        if (line.startsWith("!") || line.startsWith("<")) {
            return List.of();
        }
        String text = LINK_TARGET.matcher(line).replaceAll("]").replace("**", "");
        List<String> result = new ArrayList<>();
        for (String part : text.startsWith("|") ? text.split("\\|") : new String[] {text}) {
            result.addAll(List.of(SENTENCE_END.split(part.strip())));
        }
        return result;
    }

    // 기능 페이지가 적은 오류 코드와 테스트 이름이 코드에 있는지 본다. 코드나 테스트가 없는 저장소(검사 도구 시험)에서는 해당 검사를 건너뛴다.
    private static void checkFeatureFacts(Path root, List<String> errors) {
        Map<String, Integer> codes = errorCodes(root);
        Set<String> tests = testClassNames(root);
        for (Path page : markdownFiles(root, FEATURES_DIR, FEATURE_PAGE_NAME)) {
            String relative = relativePath(root, page);
            List<String> lines = readLines(page);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains(NOT_YET)) {
                    continue;
                }
                String location = relative + ":" + (i + 1);
                if (!codes.isEmpty()) {
                    Matcher entry = STATUS_AND_CODE.matcher(line);
                    while (entry.find()) {
                        Integer status = codes.get(entry.group(2));
                        if (status == null) {
                            errors.add(location + "  코드에 없는 오류 코드: " + entry.group(2) + " (아직 구현하지 않았다면 같은 줄에 '구현 전'이라고 적는다)");
                        } else if (status != Integer.parseInt(entry.group(1))) {
                            errors.add(location + "  오류 코드의 상태가 다르다: " + entry.group(2) + " 문서 " + entry.group(1) + ", 코드 " + status);
                        }
                    }
                    if (line.startsWith("|")) {
                        String[] cells = line.split("\\|", -1);
                        if (cells.length > 4) {
                            Matcher name = CODE_NAME.matcher(cells[2]);
                            while (name.find()) {
                                if (!codes.containsKey(name.group(1))) {
                                    errors.add(location + "  코드에 없는 오류 코드: " + name.group(1) + " (아직 구현하지 않았다면 같은 줄에 '구현 전'이라고 적는다)");
                                }
                            }
                        }
                    }
                }
                if (!tests.isEmpty()) {
                    Matcher test = TEST_NAME.matcher(line);
                    while (test.find()) {
                        if (!tests.contains(test.group(1))) {
                            errors.add(location + "  없는 테스트 클래스: " + test.group(1));
                        }
                    }
                }
            }
        }
    }

    // 기능 페이지의 "사용하는 API" 표와 OpenAPI 문서가 서로를 빠뜨리지 않는지 본다. 아직 구현하지 않은 API는 줄에 "구현 전"이라고 적는다.
    private static void checkFeatureApis(Path root, List<String> errors) {
        Path openApi = root.resolve(OPENAPI_FILE);
        if (!Files.exists(openApi)) {
            return;
        }
        Set<String> inCode = new TreeSet<>();
        String path = null;
        for (String line : readLines(openApi)) {
            Matcher p = OPENAPI_PATH.matcher(line);
            if (p.find()) {
                path = p.group(1).startsWith(API_PREFIX) ? p.group(1).substring(API_PREFIX.length()) : p.group(1);
                continue;
            }
            Matcher m = OPENAPI_METHOD.matcher(line);
            if (m.find() && path != null) {
                inCode.add(m.group(1).toUpperCase() + " " + path);
            }
        }

        Set<String> inDocs = new TreeSet<>();
        for (Path page : markdownFiles(root, FEATURES_DIR, FEATURE_PAGE_NAME)) {
            String relative = relativePath(root, page);
            List<String> lines = readLines(page);
            for (int i = 0; i < lines.size(); i++) {
                Matcher row = API_ROW.matcher(lines.get(i));
                if (!row.find()) {
                    continue;
                }
                String api = row.group(1) + " " + row.group(2);
                inDocs.add(api);
                if (!inCode.contains(api) && !FILTER_PROVIDED_APIS.contains(api) && !lines.get(i).contains(NOT_YET)) {
                    errors.add(relative + ":" + (i + 1) + "  코드에 없는 API: " + api + " (아직 구현하지 않았다면 같은 줄에 '구현 전'이라고 적는다)");
                }
            }
        }
        for (String api : inCode) {
            if (!inDocs.contains(api)) {
                errors.add(OPENAPI_FILE + "  어느 기능 페이지의 '사용하는 API'에도 없는 API: " + api);
            }
        }
    }

    // src/main의 오류 코드 enum에서 이름과 HTTP 상태를 읽는다(ErrorCode를 구현한 파일).
    private static Map<String, Integer> errorCodes(Path root) {
        Map<String, Integer> codes = new TreeMap<>();
        for (Path file : javaFiles(root, "src/main/java")) {
            List<String> lines = readLines(file);
            if (lines.stream().noneMatch(l -> l.contains("implements ErrorCode"))) {
                continue;
            }
            for (String line : lines) {
                Matcher entry = ERROR_CODE_ENTRY.matcher(line);
                if (entry.find() && HTTP_STATUS.containsKey(entry.group(2))) {
                    codes.put(entry.group(1), HTTP_STATUS.get(entry.group(2)));
                }
            }
        }
        return codes;
    }

    private static Set<String> testClassNames(Path root) {
        Set<String> names = new TreeSet<>();
        for (Path file : javaFiles(root, "src/test/java")) {
            String name = file.getFileName().toString();
            names.add(name.substring(0, name.length() - ".java".length()));
        }
        return names;
    }

    private static List<Path> javaFiles(Path root, String directory) {
        Path base = root.resolve(directory);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (var stream = Files.walk(base)) {
            return stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 문서를 옮기거나 개명하면 상대 링크가 조용히 깨지므로, 가리키는 파일이 있는지 본다(앵커는 보지 않는다).
    private static void checkMarkdownLinks(Path file, String line, String location, List<String> errors) {
        Matcher matcher = MD_LINK.matcher(line);
        while (matcher.find()) {
            String target = matcher.group(1);
            if (target.startsWith("http:") || target.startsWith("https:") || target.startsWith("mailto:") || target.startsWith("#")) {
                continue;
            }
            String path = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
            if (path.isEmpty()) {
                continue;
            }
            if (!Files.exists(file.getParent().resolve(path).normalize())) {
                errors.add(location + "  깨진 링크: " + target);
            }
        }
    }

    private static void collectDefinitions(Path root, Map<String, String> definitions, List<String> errors) {
        collectFromLines(root, DOMAIN_FILE, BOLD_RULE_DEFINITION, true, definitions, errors);
        for (Path page : markdownFiles(root, FEATURES_DIR, FEATURE_PAGE_NAME)) {
            String relative = relativePath(root, page);
            collectFromLines(root, relative, ROW_DEFINITION, false, definitions, errors);
            collectFromLines(root, relative, HEADING_DEFINITION, false, definitions, errors);
            collectFromLines(root, relative, UC_ROW_DEFINITION, false, definitions, errors);
        }
    }

    // sketch 머리말의 features와 기능 페이지 이력의 sketch 링크가 양쪽 모두 있어야 한다.
    private static void checkSketchLinks(Path root, List<String> errors) {
        Map<String, List<String>> featuresBySketch = new TreeMap<>();
        for (Path sketch : markdownFiles(root, SKETCH_DIR, SKETCH_NAME)) {
            String relative = relativePath(root, sketch);
            String name = sketch.getFileName().toString();
            List<String> features = frontMatterList(readLines(sketch), "features");
            featuresBySketch.put(name, features);
            if (features.isEmpty()) {
                errors.add(relative + ":1  머리말에 features가 없다 (기능 페이지 이름을 [01-둘러보기] 형태로 적는다. 기능과 무관한 문서 작업이면 [없음])");
            }
            for (String feature : features) {
                if (feature.equals(NO_FEATURE)) {
                    continue;
                }
                Path page = root.resolve(FEATURES_DIR).resolve(feature + ".md");
                if (!Files.isRegularFile(page)) {
                    errors.add(relative + ":1  features에 적은 기능 페이지가 없다: " + feature);
                } else if (!linkedSketches(readLines(page)).contains(name)) {
                    errors.add(relative + ":1  기능 페이지 " + feature + "의 이력에 이 sketch 링크가 없다");
                }
            }
        }
        for (Path page : markdownFiles(root, FEATURES_DIR, FEATURE_PAGE_NAME)) {
            String relative = relativePath(root, page);
            String stem = page.getFileName().toString().replaceFirst("\\.md$", "");
            for (String name : linkedSketches(readLines(page))) {
                if (!featuresBySketch.containsKey(name)) {
                    errors.add(relative + "  이력에 링크한 sketch 파일이 없다: " + name);
                } else if (!featuresBySketch.get(name).contains(stem)) {
                    errors.add(relative + "  이력에 링크한 sketch " + name + "의 features에 " + stem + "이(가) 없다");
                }
            }
        }
    }

    private static List<String> linkedSketches(List<String> lines) {
        List<String> names = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = SKETCH_LINK.matcher(line);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    // 머리말(--- 사이)에서 "key: [a, b]" 목록을 읽는다.
    private static List<String> frontMatterList(List<String> lines, String key) {
        if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
            return List.of();
        }
        for (int i = 1; i < lines.size() && !lines.get(i).trim().equals("---"); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith(key + ":")) {
                String value = line.substring(key.length() + 1).replaceAll("[\\[\\]]", "").trim();
                return value.isEmpty() ? List.of() : List.of(value.split("\\s*,\\s*"));
            }
        }
        return List.of();
    }

    private static List<Path> markdownFiles(Path root, String directory, Pattern namePattern) {
        Path start = root.resolve(directory);
        if (!Files.isDirectory(start)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(start)) {
            return list.filter(Files::isRegularFile)
                .filter(p -> namePattern.matcher(p.getFileName().toString()).matches())
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void collectFromLines(
        Path root,
        String relativeFile,
        Pattern pattern,
        boolean ignoreCodeSpans,
        Map<String, String> definitions,
        List<String> errors
    ) {
        Path file = root.resolve(relativeFile);
        if (!Files.isRegularFile(file)) {
            errors.add(relativeFile + ": 정의 파일이 없다 (문서를 옮겼다면 DocsCheck의 정의 파일 경로를 고친다)");
            return;
        }
        List<String> lines = readLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = ignoreCodeSpans ? CODE_SPAN.matcher(lines.get(i)).replaceAll(" ") : lines.get(i);
            Matcher matcher = pattern.matcher(line);
            while (matcher.find()) {
                String id = matcher.group(1);
                String location = relativeFile + ":" + (i + 1);
                String existing = definitions.putIfAbsent(id, location);
                if (existing != null) {
                    errors.add(location + "  중복 정의: " + id + " (먼저 정의한 곳 " + existing + ")");
                }
            }
        }
    }

    private static void checkLine(
        String line,
        String location,
        String relativeFile,
        Map<String, String> definitions,
        List<String> errors
    ) {
        Matcher idLike = ID_LIKE.matcher(line);
        while (idLike.find()) {
            if (!VALID_ID_FULL.matcher(idLike.group(1)).matches()) {
                errors.add(location + "  형식 오류: " + idLike.group(1));
            }
        }

        Matcher valid = VALID_ID.matcher(line);
        while (valid.find()) {
            if (!definitions.containsKey(valid.group(1))) {
                errors.add(location + "  정의 없는 참조: " + valid.group(1));
            }
        }

        if (!relativeFile.equals(DOMAIN_FILE)) {
            Matcher bold = BOLD_RULE_DEFINITION.matcher(CODE_SPAN.matcher(line).replaceAll(" "));
            while (bold.find()) {
                errors.add(location + "  정의 위치 위반: " + bold.group(1) + " (R-ID 정의는 " + DOMAIN_FILE + "에만 둔다)");
            }
        }
    }

    private static List<Path> scanFiles(Path root) {
        List<Path> files = new ArrayList<>();
        addTree(root, "docs", Set.of(".md"), files);
        addTree(root, "src", Set.of(".java", ".sql", ".yaml", ".yml", ".properties", ".md"), files);
        addTree(root, ".claude", Set.of(".md", ".json"), files);
        Path claudeMd = root.resolve("CLAUDE.md");
        if (Files.isRegularFile(claudeMd)) {
            files.add(claudeMd);
        }
        return files;
    }

    private static void addTree(Path root, String directory, Set<String> extensions, List<Path> files) {
        Path start = root.resolve(directory);
        if (!Files.isDirectory(start)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(start)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> extensions.stream().anyMatch(ext -> p.getFileName().toString().endsWith(ext)))
                .filter(p -> !isSkipped(root, p))
                .sorted()
                .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isSkipped(Path root, Path file) {
        if (relativePath(root, file).startsWith(MIGRATION_DIR)) {
            return true;
        }
        for (Path segment : root.relativize(file)) {
            if (SKIPPED_DIRS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static List<String> readLines(Path file) {
        try {
            return List.of(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\r?\n", -1));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sectionToken(String line) {
        Matcher matcher = SECTION_REF.matcher(line);
        return matcher.find() ? line.substring(matcher.start(), Math.min(line.length(), matcher.end() + 6)).trim() : "";
    }

    private static int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String countByKind(Map<String, String> definitions) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String kind : List.of("P", "OP", "UC", "R")) {
            counts.put(kind, 0);
        }
        for (String id : definitions.keySet()) {
            String kind = id.startsWith("R-") ? "R" : id.substring(0, id.indexOf('-'));
            counts.merge(kind, 1, Integer::sum);
        }
        return counts.toString();
    }

}
