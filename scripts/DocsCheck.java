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
 * § 번호 참조는 개수만 보고한다.
 *
 * <p>사용법: {@code scripts/docs-check [--root <디렉터리>]} (저장소 루트에서 실행)
 */
public class DocsCheck {

    static final String AGGREGATES = "MEMBER|POLL|VOTE|REPORT|ACTION";

    // 정의 파일. 문서를 기능 단위로 다시 나눌 때 이 경로를 함께 고친다.
    static final String POLICY_FILE = "docs/예외-정책.md";
    static final String SCENARIO_FILE = "docs/사용자-시나리오.md";
    static final String DOMAIN_FILE = "docs/도메인-모델.md";

    // 표의 첫 칸이 ID로 시작하는 행이 정의다. (P-28처럼 아직 정하지 않은 항목도 이 형태로 정의한다)
    static final Pattern ROW_DEFINITION = Pattern.compile("^\\|\\s*((?:P|OP)-\\d{2})(?![A-Za-z0-9])");
    static final Pattern HEADING_DEFINITION = Pattern.compile("^#{3}\\s+(UC-\\d{2})(?![A-Za-z0-9])");
    static final Pattern BOLD_RULE_DEFINITION = Pattern.compile("\\*\\*(R-(?:" + AGGREGATES + ")-\\d{2})\\*\\*");

    static final Pattern VALID_ID_FULL = Pattern.compile("(?:P|OP|UC)-\\d{2}|R-(?:" + AGGREGATES + ")-\\d{2}");
    static final Pattern VALID_ID = Pattern.compile(
        "(?<![A-Za-z0-9_-])((?:P|OP|UC)-\\d{2}|R-(?:" + AGGREGATES + ")-\\d{2})(?![A-Za-z0-9])");
    static final Pattern ID_LIKE = Pattern.compile(
        "(?<![A-Za-z0-9_-])((?:P|OP|UC)-\\d+|R-[A-Za-z]+-\\d+)(?![A-Za-z0-9])");

    static final Pattern CODE_SPAN = Pattern.compile("`[^`]*`");
    static final Pattern SECTION_REF = Pattern.compile("§\\s?\\d");

    static final Set<String> SKIPPED_DIRS = Set.of("_workspace", "build", ".git", ".gradle", "node_modules", "out");
    static final List<String> AREAS = List.of("docs", "src", ".claude", "CLAUDE.md");

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
        out.println("§ 참조(보고만 함): " + result.sectionRefs());

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
                checkLine(lines.get(i), relative + ":" + (i + 1), relative, definitions, errors);
                sectionRefs.merge(area, countMatches(SECTION_REF, lines.get(i)), Integer::sum);
            }
        }
        return new Result(definitions, errors, sectionRefs);
    }

    private static void collectDefinitions(Path root, Map<String, String> definitions, List<String> errors) {
        collectFromLines(root, POLICY_FILE, ROW_DEFINITION, false, definitions, errors);
        collectFromLines(root, SCENARIO_FILE, HEADING_DEFINITION, false, definitions, errors);
        collectFromLines(root, DOMAIN_FILE, BOLD_RULE_DEFINITION, true, definitions, errors);
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
