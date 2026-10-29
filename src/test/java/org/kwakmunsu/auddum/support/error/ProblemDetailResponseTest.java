package org.kwakmunsu.auddum.support.error;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.WebApiTestSupport;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전역 예외 처리(@RestControllerAdvice)가 만드는 오류 응답 형식을 검증한다.
 */
@Import(ProblemDetailResponseTest.ErrorProbeController.class)
class ProblemDetailResponseTest extends WebApiTestSupport {

    @Test
    void 오류는_application_problem_json으로_내려간다() {
        mvcTester.get()
                .uri("/probe/auddum/VALIDATION_FAILED")
                .assertThat()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void 프로젝트_예외는_ProblemDetail_기본_필드를_채운다() {
        mvcTester.get()
                .uri("/probe/auddum/VALIDATION_FAILED")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .apply(print())
                .bodyJson()
                // type이 about:blank이면 Spring이 필드를 생략한다(RFC 7807에서 생략은 about:blank와 같다).
                .doesNotHavePath("$.type")
                .hasPathSatisfying("$.title", v -> v.assertThat().isEqualTo("Bad Request"))
                .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo(400))
                .hasPathSatisfying("$.detail", v -> v.assertThat().isNotNull());
    }

    @Test
    void instance는_요청_경로다() {
        mvcTester.get()
                .uri("/probe/auddum/VALIDATION_FAILED")
                .assertThat()
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.instance", v -> v.assertThat().isEqualTo("/probe/auddum/VALIDATION_FAILED"));
    }

    @Test
    void MALFORMED_REQUEST_예외는_400과_코드를_내린다() {
        mvcTester.get()
                .uri("/probe/auddum/MALFORMED_REQUEST")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("MALFORMED_REQUEST"));
    }

    @Test
    void UNAUTHENTICATED_예외는_401과_코드를_내린다() {
        mvcTester.get()
                .uri("/probe/auddum/UNAUTHENTICATED")
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("UNAUTHENTICATED"))
                .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo(401));
    }

    @Test
    void FORBIDDEN_예외는_403과_코드를_내린다() {
        mvcTester.get()
                .uri("/probe/auddum/FORBIDDEN")
                .assertThat()
                .hasStatus(HttpStatus.FORBIDDEN)
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("FORBIDDEN"))
                .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo(403));
    }

    @Test
    void INTERNAL_ERROR_코드를_직접_던져도_500으로_내려간다() {
        mvcTester.get()
                .uri("/probe/auddum/INTERNAL_ERROR")
                .assertThat()
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("INTERNAL_ERROR"));
    }

    @Test
    void 입력_검증_실패는_400_VALIDATION_FAILED와_errors를_내린다() {
        mvcTester.post()
                .uri("/probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "질문", "options": ["하나"]}
                        """)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("VALIDATION_FAILED"))
                .hasPathSatisfying("$.instance", v -> v.assertThat().isEqualTo("/probe/validate"));
    }

    @Test
    void 필드_정보를_가진_AuddumException도_errors를_내린다() {
        mvcTester.get()
                .uri("/probe/auddum-validation")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("VALIDATION_FAILED"))
                .hasPathSatisfying("$.errors[0].field", v -> v.assertThat().isEqualTo("closesAt"))
                .hasPathSatisfying("$.errors[0].reason", v -> v.assertThat().isEqualTo("마감 시각은 현재보다 뒤여야 합니다."));
    }

    @Test
    void errors의_각_항목은_field와_reason을_가진다() {
        mvcTester.post()
                .uri("/probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "질문", "options": ["하나"]}
                        """)
                .assertThat()
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.errors", v -> v.assertThat().asList().hasSize(1))
                .hasPathSatisfying("$.errors[0].field", v -> v.assertThat().isEqualTo("options"))
                .hasPathSatisfying("$.errors[0].reason", v -> v.assertThat().isEqualTo("선택지는 2~5개여야 합니다."));
    }

    @Test
    void 여러_필드가_틀리면_errors에_필드마다_담긴다() {
        mvcTester.post()
                .uri("/probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "", "options": ["하나"]}
                        """)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.errors", v -> v.assertThat().asList().hasSize(2))
                .hasPathSatisfying("$.errors[*].field",
                        v -> v.assertThat().asList().containsExactlyInAnyOrder("title", "options"));
    }

    @Test
    void 검증을_통과하면_오류_응답이_아니다() {
        mvcTester.post()
                .uri("/probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "질문", "options": ["하나", "둘"]}
                        """)
                .assertThat()
                .hasStatus(HttpStatus.OK);
    }

    @Test
    void 입력_검증_실패가_아닌_오류에는_errors가_없다() {
        mvcTester.get()
                .uri("/probe/auddum/FORBIDDEN")
                .assertThat()
                .apply(print())
                .bodyJson()
                .doesNotHavePath("$.errors");
    }

    @Test
    void 깨진_JSON_본문은_400_MALFORMED_REQUEST다() {
        mvcTester.post()
                .uri("/probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": ")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("MALFORMED_REQUEST"));
    }

    @Test
    void 경로_변수_형식_오류는_400_MALFORMED_REQUEST다() {
        mvcTester.get()
                .uri("/probe/number/abc")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("MALFORMED_REQUEST"));
    }

    @Test
    void 예상하지_못한_예외는_500_INTERNAL_ERROR로_내려간다() {
        mvcTester.get()
                .uri("/probe/unexpected")
                .assertThat()
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .apply(print())
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("INTERNAL_ERROR"))
                .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo(500));
    }

    @Test
    void 내부_오류_응답은_예외_메시지와_클래스를_노출하지_않는다() {
        mvcTester.get()
                .uri("/probe/unexpected")
                .assertThat()
                .apply(print())
                .bodyText()
                .doesNotContain("SECRET-DB-PASSWORD")
                .doesNotContain("IllegalStateException")
                .doesNotContain("ProblemDetailResponseTest");
    }

    @Test
    void 내부_오류_응답에는_errors가_없다() {
        mvcTester.get()
                .uri("/probe/unexpected")
                .assertThat()
                .apply(print())
                .bodyJson()
                .doesNotHavePath("$.errors");
    }

    @RestController
    static class ErrorProbeController {

        @GetMapping("/probe/auddum/{code}")
        String throwAuddum(@PathVariable String code) {
            throw new AuddumException(DefaultErrorCode.valueOf(code));
        }

        @GetMapping("/probe/auddum-validation")
        String throwValidation() {
            throw AuddumException.validation("closesAt", "마감 시각은 현재보다 뒤여야 합니다.");
        }

        @GetMapping("/probe/number/{id}")
        String number(@PathVariable long id) {
            return String.valueOf(id);
        }

        @GetMapping("/probe/unexpected")
        String unexpected() {
            throw new IllegalStateException("SECRET-DB-PASSWORD");
        }

        @PostMapping("/probe/validate")
        String validate(@Valid @RequestBody ProbeRequest request) {
            return "ok";
        }

    }

    record ProbeRequest(
            @NotBlank(message = "제목은 필수입니다.") String title,
            @Size(min = 2, max = 5, message = "선택지는 2~5개여야 합니다.") List<String> options
    ) {

    }

}
