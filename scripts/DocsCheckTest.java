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
        "docs/features/01-둘러보기.md", "| UC-02 | 상세 조회 | 보여 준다 |\n| P-01 | 숨김 정책 | UC-02 |\n| OP-01 | 신고 접수 | 검토 |\n",
        "docs/domain/도메인-모델.md", "- **R-POLL-01** 질문은 1~500자\n"
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
        기능_페이지의_정의도_모은다();
        sketch과_기능_페이지가_서로_링크하면_통과한다();
        sketch만_링크하면_실패한다();
        기능_페이지만_링크하면_실패한다();
        양식_파일은_검사하지_않는다();
        깨진_상대_링크를_잡는다();
        퇴역한_문서_이름을_잡는다();
        기능_페이지의_오류_코드와_상태를_코드와_맞춰_본다();
        기능_페이지의_테스트_클래스_이름을_코드와_맞춰_본다();

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
        var result = DocsCheck.check(fixture(Map.of("docs/features/02-투표-만들기.md", policy)));
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
        var result = DocsCheck.check(fixture(Map.of("docs/features/01-둘러보기.md", "| UC-02 | 상세 조회 | 보여 준다 |\n" + policy, "docs/기능.md", "미확정은 P-28이다\n")));
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
        Files.delete(root.resolve("docs/domain/도메인-모델.md"));
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
            ".claude/skills/x/SKILL.md", "아키텍처 §3을 읽는다\n",
            "CLAUDE.md", "테스트 §1\n")));
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

        var generated = DocsCheck.check(fixture(Map.of("src/test/java/F.java", "// 쓰는 곳: docs/generated/오류-코드.md\nclass F {}\n")));
        expect("생성 문서 경로는 생성기가 알아야 하므로 통과한다", generated.errors().isEmpty(), generated.errors().toString());
    }

    static void 적용된_마이그레이션은_검사하지_않는다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of("src/main/resources/db/migration/V1__x.sql", "-- 문서 §4 UC-77 P-99\n")));
        expect("마이그레이션 폴더는 검사하지 않는다", result.errors().isEmpty(), result.errors().toString());
    }

    static final String SKETCH = "---\nissue: 7\nfeatures: [03-참여하기]\nids: [P-01]\n---\n# sketch\n";
    static final String PAGE_WITH_LINK = "# 참여하기\n| #7 | 변경 | [sketch](../records/sketches/7-join.md) |\n";

    static void 기능_페이지의_정의도_모은다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of(
            "docs/features/03-참여하기.md", "### UC-03 투표 참여\n| P-08 | 중복 참여 | UC-03 |\n",
            "docs/기능.md", "UC-03과 P-08을 본다\n")));
        expect("기능 페이지의 UC·P 정의를 모은다", result.errors().isEmpty(), result.errors().toString());
        expect("UC-03이 기능 페이지에 정의된다",
            "docs/features/03-참여하기.md:1".equals(result.definitions().get("UC-03")), result.definitions().toString());

        var row = DocsCheck.check(fixture(Map.of(
            "docs/features/03-참여하기.md", "| UC-03 | 비회원 참여 | 201 |\n",
            "docs/기능.md", "UC-03을 본다\n")));
        expect("기능 페이지는 UC를 표의 첫 칸으로도 정의할 수 있다", row.errors().isEmpty(), row.errors().toString());
        expect("표로 정의한 UC-03이 정의에 있다", row.definitions().containsKey("UC-03"), row.definitions().toString());

        var dup = DocsCheck.check(fixture(Map.of("docs/features/03-참여하기.md", "| P-01 | 또 정의 | UC-02 |\n")));
        expect("기능 페이지와 정책 문서의 중복 정의를 잡는다", hasError(dup, "중복 정의", "P-01"), dup.errors().toString());
    }

    static void sketch과_기능_페이지가_서로_링크하면_통과한다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of(
            "docs/records/sketches/7-join.md", SKETCH, "docs/features/03-참여하기.md", PAGE_WITH_LINK)));
        expect("양쪽 링크가 맞으면 통과한다", result.errors().isEmpty(), result.errors().toString());
    }

    static void sketch만_링크하면_실패한다() throws IOException {
        var noLink = DocsCheck.check(fixture(Map.of(
            "docs/records/sketches/7-join.md", SKETCH, "docs/features/03-참여하기.md", "# 참여하기\n")));
        expect("기능 페이지 이력에 링크가 없으면 실패한다",
            hasError(noLink, "7-join.md:1", "이력에 이 sketch 링크가 없다"), noLink.errors().toString());

        var noPage = DocsCheck.check(fixture(Map.of("docs/records/sketches/7-join.md", SKETCH)));
        expect("features에 적은 페이지가 없으면 실패한다",
            hasError(noPage, "7-join.md:1", "기능 페이지가 없다", "03-참여하기"), noPage.errors().toString());

        var noFeatures = DocsCheck.check(fixture(Map.of("docs/records/sketches/7-join.md", "# 머리말 없음\n")));
        expect("머리말에 features가 없으면 실패한다", hasError(noFeatures, "머리말에 features가 없다"), noFeatures.errors().toString());

        var docsOnly = DocsCheck.check(fixture(Map.of("docs/records/sketches/7-join.md", SKETCH.replace("[03-참여하기]", "[없음]"))));
        expect("features가 [없음]이면 기능 페이지 링크를 요구하지 않는다", docsOnly.errors().isEmpty(), docsOnly.errors().toString());
    }

    static void 기능_페이지만_링크하면_실패한다() throws IOException {
        var missing = DocsCheck.check(fixture(Map.of("docs/features/03-참여하기.md", PAGE_WITH_LINK)));
        expect("링크한 sketch 파일이 없으면 실패한다",
            hasError(missing, "03-참여하기.md", "링크한 sketch 파일이 없다", "7-join.md"), missing.errors().toString());

        var other = DocsCheck.check(fixture(Map.of(
            "docs/records/sketches/7-join.md", SKETCH.replace("03-참여하기", "01-둘러보기"),
            "docs/features/01-둘러보기.md", PAGE_WITH_LINK,
            "docs/features/03-참여하기.md", PAGE_WITH_LINK)));
        expect("sketch features에 없는 페이지가 링크하면 실패한다",
            hasError(other, "03-참여하기.md", "features에 03-참여하기이(가) 없다"), other.errors().toString());
    }

    static void 양식_파일은_검사하지_않는다() throws IOException {
        var result = DocsCheck.check(fixture(Map.of(
            "docs/records/sketches/_양식.md", "# 양식\n",
            "docs/features/_양식.md", "[sketch](../records/sketches/0-x.md)\n",
            "docs/features/README.md", "양식과 README는 건너뛴다\n")));
        expect("_로 시작하는 양식과 README는 건너뛴다", result.errors().isEmpty(), result.errors().toString());
    }

    static void 깨진_상대_링크를_잡는다() throws IOException {
        var bad = DocsCheck.check(fixture(Map.of("docs/기능.md", "[없는 문서](없는.md)와 [앵커](#절) [밖](https://example.com)\n")));
        expect("없는 파일 링크는 실패다", hasError(bad, "docs/기능.md:1", "깨진 링크", "없는.md"), bad.errors().toString());
        expect("앵커·외부 링크는 통과한다", bad.errors().stream().noneMatch(e -> e.contains("앵커") || e.contains("example")), bad.errors().toString());

        var ok = DocsCheck.check(fixture(Map.of("docs/기능.md", "[도메인](domain/도메인-모델.md#절)\n")));
        expect("있는 파일 링크는 통과한다", ok.errors().isEmpty(), ok.errors().toString());

        var template = DocsCheck.check(fixture(Map.of("docs/features/_양식.md", "[sketch](../records/<n>.md)\n")));
        expect("양식(_로 시작)은 링크를 검사하지 않는다", template.errors().isEmpty(), template.errors().toString());
    }

    static void 퇴역한_문서_이름을_잡는다() throws IOException {
        var docs = DocsCheck.check(fixture(Map.of("docs/기능.md", "예외-정책과 사용자 시나리오 명세서를 본다\n")));
        expect("docs의 퇴역 이름은 실패다", hasError(docs, "docs/기능.md:1", "퇴역한 문서 이름", "예외-정책", "기능 페이지"), docs.errors().toString());
        expect("긴 옛 제목도 잡는다", hasError(docs, "퇴역한 문서 이름", "사용자 시나리오 명세서"), docs.errors().toString());

        var harness = DocsCheck.check(fixture(Map.of(".claude/skills/x/SKILL.md", "`docs/개발-가이드.md`를 본다\n", "CLAUDE.md", "인증-중복-참여\n")));
        expect(".claude의 퇴역 이름은 실패다", hasError(harness, ".claude/skills/x/SKILL.md:1", "개발-가이드"), harness.errors().toString());
        expect("CLAUDE.md의 퇴역 이름도 실패다", hasError(harness, "CLAUDE.md:1", "인증-중복-참여"), harness.errors().toString());

        var records = DocsCheck.check(fixture(Map.of("docs/records/decisions/1-a.md", "예외-정책에서 옮겼다\n")));
        expect("시점 기록(records)은 검사하지 않는다", records.errors().isEmpty(), records.errors().toString());
    }

    static final String ENUM = "public enum PollErrorCode implements ErrorCode {\n    POLL_CLOSED (HttpStatus.CONFLICT, \"마감\", LogLevel.INFO),\n    INVALID_OPTION (HttpStatus.BAD_REQUEST, \"선택지\", LogLevel.INFO),\n}\n";
    static final String PAGE_HEAD = "| UC-02 | 상세 조회 | 보여 준다 |\n| P-01 | 숨김 정책 | UC-02 |\n| OP-01 | 신고 접수 | 검토 |\n";

    static void 기능_페이지의_오류_코드와_상태를_코드와_맞춰_본다() throws IOException {
        var ok = DocsCheck.check(fixture(Map.of(
            "src/main/java/PollErrorCode.java", ENUM,
            "docs/features/01-둘러보기.md", PAGE_HEAD + "| 마감 뒤 참여 | 거절(409 `POLL_CLOSED`) | `INVALID_OPTION` |\n")));
        expect("코드와 상태가 같으면 통과한다", ok.errors().isEmpty(), ok.errors().toString());

        var status = DocsCheck.check(fixture(Map.of(
            "src/main/java/PollErrorCode.java", ENUM,
            "docs/features/01-둘러보기.md", PAGE_HEAD + "| 마감 뒤 참여 | 거절(400 `POLL_CLOSED`) | - |\n")));
        expect("상태가 다르면 실패다", hasError(status, "01-둘러보기.md:4", "상태가 다르다", "POLL_CLOSED", "문서 400", "코드 409"), status.errors().toString());

        var missing = DocsCheck.check(fixture(Map.of(
            "src/main/java/PollErrorCode.java", ENUM,
            "docs/features/01-둘러보기.md", PAGE_HEAD + "| 마감 뒤 참여 | 거절(409 `POLL_GONE`) | - |\n| 어긋나면 | `POLL_MISSING` | - |\n")));
        expect("코드에 없는 코드는 실패다", hasError(missing, "01-둘러보기.md:4", "코드에 없는 오류 코드", "POLL_GONE"), missing.errors().toString());
        expect("규칙 표의 둘째 칸 코드도 본다", hasError(missing, "01-둘러보기.md:5", "POLL_MISSING"), missing.errors().toString());

        var notYet = DocsCheck.check(fixture(Map.of(
            "src/main/java/PollErrorCode.java", ENUM,
            "docs/features/01-둘러보기.md", PAGE_HEAD + "| 복구 | 거절(409 `POLL_NOT_HIDDEN`) | 구현 전 |\n")));
        expect("구현 전이라고 적은 줄은 넘어간다", notYet.errors().isEmpty(), notYet.errors().toString());

        var noEnum = DocsCheck.check(fixture(Map.of("docs/features/01-둘러보기.md", PAGE_HEAD + "| 참여 | 거절(409 `POLL_CLOSED`) | - |\n")));
        expect("오류 코드 enum이 없는 저장소에서는 건너뛴다", noEnum.errors().isEmpty(), noEnum.errors().toString());
    }

    static void 기능_페이지의_테스트_클래스_이름을_코드와_맞춰_본다() throws IOException {
        var ok = DocsCheck.check(fixture(Map.of(
            "src/test/java/org/x/VoteTest.java", "class VoteTest {}\n",
            "docs/features/01-둘러보기.md", PAGE_HEAD + "| 규칙 | 거절 | P-01 · `VoteTest` |\n")));
        expect("있는 테스트 클래스는 통과한다", ok.errors().isEmpty(), ok.errors().toString());

        var bad = DocsCheck.check(fixture(Map.of(
            "src/test/java/org/x/VoteTest.java", "class VoteTest {}\n",
            "docs/features/01-둘러보기.md", PAGE_HEAD + "| 규칙 | 거절 | `VoteGoneTest` |\n")));
        expect("없는 테스트 클래스는 실패다", hasError(bad, "01-둘러보기.md:4", "없는 테스트 클래스", "VoteGoneTest"), bad.errors().toString());
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
