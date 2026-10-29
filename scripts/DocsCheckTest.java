import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DocsCheck가 정상 입력은 통과시키고 틀린 입력은 실제로 잡는지 확인한다. 실행: scripts/test-docs-check
 */
public class DocsCheckTest {

    static final Map<String, String> BASE = Map.of(
        "docs/예외-정책.md", "| ID | 확정 내용 | 관련 UC |\n| --- | --- | --- |\n| P-01 | 숨김 정책 | UC-02 |\n| OP-01 | 신고 접수 | 검토 |\n",
        "docs/사용자-시나리오.md", "### UC-02 상세 조회\n",
        "docs/도메인-모델.md", "- **R-POLL-01** 질문은 1~500자\n"
    );

    static final List<String> failures = new ArrayList<>();
    static int total = 0;

    public static void main(String[] args) throws IOException {
        정상_입력은_오류가_없다();
        정의_없는_참조를_잡는다();
        코드_주석의_정의_없는_참조도_잡는다();
        중복_정의를_잡는다();
        형식_오류를_잡는다();
        도메인_모델_밖의_R_정의를_잡는다();
        코드_스팬_안의_굵은_R_ID는_정의로_세지_않는다();
        표의_첫_칸이_ID인_행은_정의다();
        색인_표는_정의로_세지_않는다();
        정의_파일이_없으면_실패한다();
        작업_폴더는_검사하지_않는다();
        절_번호_참조는_docs와_src에서_실패한다();
        하네스_문서의_절_번호_참조는_보고만_한다();
        적용된_마이그레이션은_검사하지_않는다();
        src의_문서_이름_언급은_실패한다();

        System.out.println(total + "개 중 " + (total - failures.size()) + "개 통과");
        failures.forEach(f -> System.out.println("실패: " + f));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    static void 정상_입력은_오류가_없다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of()));
        expect("정상 입력은 오류가 없다", result.errors().isEmpty(), result.errors().toString());
        expect("정의 4개를 모은다", result.definitions().size() == 4, result.definitions().toString());
    }

    static void 정의_없는_참조를_잡는다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of("docs/기능.md", "마감은 (P-99)를 따른다\n")));
        expect("정의 없는 참조를 잡는다", hasError(result, "docs/기능.md:1", "정의 없는 참조", "P-99"), result.errors().toString());
    }

    static void 코드_주석의_정의_없는_참조도_잡는다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of("src/main/java/A.java", "class A {\n    // UC-77 참조\n}\n")));
        expect("src 주석의 정의 없는 참조를 잡는다",
            hasError(result, "src/main/java/A.java:2", "UC-77"), result.errors().toString());
    }

    static void 중복_정의를_잡는다() throws IOException {
        var policy = "| P-01 | 숨김 정책 | UC-02 |\n| P-01 | 같은 번호를 또 정의 | UC-02 |\n";
        var result = DocsCheck.check(fixture(Map.of("docs/예외-정책.md", policy)));
        expect("중복 정의를 잡는다", hasError(result, "중복 정의", "P-01"), result.errors().toString());
    }

    static void 형식_오류를_잡는다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of("docs/기능.md", "R-POLL-3 P-1 R-FOO-01 UC-100\n")));
        for (String bad : List.of("R-POLL-3", "P-1", "R-FOO-01", "UC-100")) {
            expect("형식 오류를 잡는다: " + bad, hasError(result, "형식 오류", bad), result.errors().toString());
        }
    }

    static void 도메인_모델_밖의_R_정의를_잡는다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of("docs/기능.md", "- **R-POLL-01** 다른 문서에서 정의\n")));
        expect("도메인-모델 밖의 R-ID 정의를 잡는다", hasError(result, "정의 위치 위반", "R-POLL-01"), result.errors().toString());
    }

    static void 코드_스팬_안의_굵은_R_ID는_정의로_세지_않는다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of("docs/기능.md", "예: `**R-POLL-01**`로 쓴다\n")));
        expect("코드 스팬 안의 굵은 R-ID는 정의로 세지 않는다", result.errors().isEmpty(), result.errors().toString());
    }

    static void 표의_첫_칸이_ID인_행은_정의다() throws IOException {
        var policy = "| P-01 | 숨김 정책 | UC-02 |\n| P-28 운영 세부 값 | 구현 단계 | 세부 값 |\n| OP-01 | 신고 접수 | 검토 |\n";
        var result = DocsCheck.check(fixture(Map.of("docs/예외-정책.md", policy, "docs/기능.md", "미확정은 P-28이다\n")));
        expect("첫 칸이 ID로 시작하는 행을 정의로 센다", result.errors().isEmpty(), result.errors().toString());
        expect("P-28이 정의에 있다", result.definitions().containsKey("P-28"), result.definitions().toString());
    }

    static void 색인_표는_정의로_세지_않는다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of("docs/README.md", "| OP-02 | 색인에만 있는 시나리오 |\n")));
        expect("README의 색인 행은 정의가 아니라 정의 없는 참조로 잡힌다",
            hasError(result, "docs/README.md:1", "정의 없는 참조", "OP-02"), result.errors().toString());
    }

    static void 정의_파일이_없으면_실패한다() throws IOException {
        var root = fixture(Map.of());
        Files.delete(root.resolve("docs/도메인-모델.md"));
        var result = DocsCheck.check(root);
        expect("정의 파일이 없으면 실패한다", hasError(result, "정의 파일이 없다", "도메인-모델.md"), result.errors().toString());
    }

    static void 작업_폴더는_검사하지_않는다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of("_workspace/run/note.md", "P-99 UC-77\n")));
        expect("_workspace는 검사하지 않는다", result.errors().isEmpty(), result.errors().toString());
    }

    static void 절_번호_참조는_docs와_src에서_실패한다() throws IOException {
        var docs = DocsCheck.check(fixture(Map.of("docs/기능.md", "도메인-모델 §3 과 § 4를 본다\n")));
        expect("docs의 절 번호 참조는 실패다", hasError(docs, "docs/기능.md:1", "절 번호 참조"), docs.errors().toString());
        expect("절 번호 참조를 센다", docs.sectionRefs().get("docs") == 2, docs.sectionRefs().toString());

        var src = DocsCheck.check(fixture(Map.of("src/main/java/B.java", "// 아키텍처 §2를 따른다\nclass B {}\n")));
        expect("src의 절 번호 참조는 실패다", hasError(src, "src/main/java/B.java:1", "절 번호 참조"), src.errors().toString());
    }

    static void 하네스_문서의_절_번호_참조는_보고만_한다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of(
            ".claude/skills/x/SKILL.md", "도메인-모델 §3을 읽는다\n",
            "CLAUDE.md", "개발-가이드 §1\n")));
        expect("하네스 문서의 절 번호 참조는 실패가 아니다", result.errors().isEmpty(), result.errors().toString());
        expect(".claude 참조를 센다", result.sectionRefs().get(".claude") == 1, result.sectionRefs().toString());
        expect("CLAUDE.md 참조를 센다", result.sectionRefs().get("CLAUDE.md") == 1, result.sectionRefs().toString());
    }

    static void src의_문서_이름_언급은_실패한다() throws IOException {
        var bad = DocsCheck.check(fixture(Map.of("src/main/java/C.java", "// API-명세 GET /polls, ERD report 테이블\nclass C {}\n")));
        expect("src의 문서 이름은 실패다", hasError(bad, "src/main/java/C.java:1", "문서 이름 언급"), bad.errors().toString());

        var path = DocsCheck.check(fixture(Map.of("src/test/java/D.java", "// 기대값은 docs/ERD.md\nclass D {}\n")));
        expect("docs 경로도 실패다", hasError(path, "src/test/java/D.java:1", "문서 이름 언급"), path.errors().toString());

        var ok = DocsCheck.check(fixture(Map.of(
            "src/main/java/E.java", "// ERDiagram, WORLD, docs가 정하지 않았다\nclass E {}\n",
            "docs/기능.md", "API-명세와 ERD를 본다\n")));
        expect("일반 낱말과 docs 본문은 통과한다", ok.errors().isEmpty(), ok.errors().toString());
    }

    static void 적용된_마이그레이션은_검사하지_않는다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of("src/main/resources/db/migration/V1__x.sql", "-- 문서 §4 UC-77 P-99\n")));
        expect("마이그레이션 폴더는 검사하지 않는다", result.errors().isEmpty(), result.errors().toString());
    }

    static Path fixture(Map<String, String> overrides) throws IOException {
        Path root = Files.createTempDirectory("docs-check-test");
        Map<String, String> all = new LinkedHashMap<>(BASE);
        all.putAll(overrides);
        for (var entry : all.entrySet()) {
            Path file = root.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue());
        }
        return root;
    }

    static boolean hasError(DocsCheck.Result result, String... parts) {
        return result.errors().stream().anyMatch(e -> List.of(parts).stream().allMatch(e::contains));
    }

    static void expect(String name, boolean ok, String detail) {
        total++;
        if (!ok) {
            failures.add(name + " -> " + detail);
        }
    }

}
