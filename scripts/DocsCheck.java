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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 문서와 코드 주석의 ID(P-xx, OP-xx, UC-xx, R-애그리거트-xx)를 검사한다.
 *
 * <p>정의는 정해진 파일에서만 모은다. 정의가 없는 참조, 중복 정의, 형식 오류, 도메인-모델 밖의 R-ID 정의는 실패다.
 * 기능 페이지(docs/features)의 P·OP·UC 정의도 모으고, sketch(docs/records/sketches)의 머리말과 기능 페이지의 이력 링크가 서로를 가리키는지 본다.
 * src의 문서 이름 언급은 실패다. 절 번호(§) 참조는 docs와 src에서는 실패이고, .claude와 CLAUDE.md는 개수만 보고한다(하네스는 1단계에서 정리한다).
 *
 * <p>사용법: {@code scripts/docs-check [--root <디렉터리>]} (저장소 루트에서 실행)
 */
public class DocsCheck {

    static final String AGGREGATES = "MEMBER|POLL|VOTE|REPORT|ACTION";

    // 정의 파일. 문서를 기능 단위로 다시 나눌 때 이 경로를 함께 고친다.
    static final String POLICY_FILE = "docs/engineering/예외-응답.md";
    static final String SCENARIO_FILE = "docs/사용자-시나리오.md";
    static final String DOMAIN_FILE = "docs/domain/도메인-모델.md";

    // 기능 페이지(01-둘러보기.md처럼 번호로 시작)와 sketch(이슈번호로 시작)만 검사한다. 양식(_로 시작)과 README는 건너뛴다.
    static final String FEATURES_DIR = "docs/features";
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
    static final Pattern DOC_NAME = Pattern.compile(
        "API-명세|인증-중복-참여|도메인-모델|예외-정책|설계-결정|사용자-시나리오|테스트-배포|개발-가이드"
            + "|아키텍처 설계서|(?<![A-Za-z0-9_])ERD(?![A-Za-z0-9_])|docs/\\S+\\.md");
    static final Set<String> DOC_NAME_FAIL_AREAS = Set.of("src");

    static final Set<String> SKIPPED_DIRS = Set.of("_workspace", "build", ".git", ".gradle", "node_modules", "out");
    static final List<String> AREAS = List.of("docs", "src", ".claude", "CLAUDE.md");
    static final Set<String> SECTION_REF_FAIL_AREAS = Set.of("docs", "src");

    // 이미 적용된 Flyway 마이그레이션은 주석 한 줄만 바꿔도 체크섬이 달라져 검증이 실패하므로 검사하지 않는다.
    static final String MIGRATION_DIR = "src/main/resources/db/migration/";

    record Result(Map<String, String> definitions, List<String> errors, Map<String, Integer> sectionRefs) {
    }

    public static void main(String[] args) {
        Path root = Path.of(".");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--root") && i + 1 < args.length) {
                root = Path.of(args[++i]);
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

        System.exit(result.errors().isEmpty() ? 0 : 1);
    }

    static Result check(Path root) {
        List<String> errors = new ArrayList<>();
        Map<String, String> definitions = new TreeMap<>();
        collectDefinitions(root, definitions, errors);

        Map<String, Integer> sectionRefs = new LinkedHashMap<>();
        AREAS.forEach(area -> sectionRefs.put(area, 0));

        for (Path file : scanFiles(root)) {
            String relative = relativePath(root, file);
            String area = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : relative;
            List<String> lines = readLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String location = relative + ":" + (i + 1);
                checkLine(line, location, relative, definitions, errors);

                Matcher docName = DOC_NAME.matcher(line);
                if (docName.find() && DOC_NAME_FAIL_AREAS.contains(area)) {
                    errors.add(location + "  문서 이름 언급: " + docName.group() + " (정책·규칙·시나리오 ID나 코드 식별자로 바꾸거나 지운다)");
                }

                int sections = countMatches(SECTION_REF, line);
                sectionRefs.merge(area, sections, Integer::sum);
                if (sections > 0 && SECTION_REF_FAIL_AREAS.contains(area)) {
                    errors.add(location + "  절 번호 참조: " + sectionToken(line) + " (정책·규칙·시나리오 ID로 바꾸거나 지운다)");
                }
            }
        }
        checkSketchLinks(root, errors);
        return new Result(definitions, errors, sectionRefs);
    }

    private static void collectDefinitions(Path root, Map<String, String> definitions, List<String> errors) {
        collectFromLines(root, POLICY_FILE, ROW_DEFINITION, false, definitions, errors);
        collectFromLines(root, SCENARIO_FILE, HEADING_DEFINITION, false, definitions, errors);
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
                errors.add(relative + ":1  머리말에 features가 없다 (기능 페이지 이름을 [01-둘러보기] 형태로 적는다)");
            }
            for (String feature : features) {
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
