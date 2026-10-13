package org.kwakmunsu.auddum.poll.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.kwakmunsu.auddum.WebApiTestSupport;
import org.kwakmunsu.auddum.poll.application.dto.NewPoll;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * POST /api/v1/polls 웹 계층(서비스는 목). API 명세 §3, §7.
 * 작성자는 임시로 X-Member-Id 헤더에서 읽는다(brief 결정 사항).
 */
class PollWebApiTest extends WebApiTestSupport {

    private static final String 본문 = """
        {"question": "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?",
         "category": "SOME",
         "options": ["관심 없음", "그냥 바쁜 것", "더 지켜봐야 함"]}
        """;

    @Test
    void 생성하면_201과_Location을_내린다() {
        var poll = 투표를_만든다(null);
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L))).willReturn(poll);

        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(본문)
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .hasHeader("Location", "/api/v1/polls/" + poll.getPublicId().value());
    }

    @Test
    void 본문은_공개_ID_문자열_하나다() {
        var poll = 투표를_만든다(null);
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L))).willReturn(poll);

        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(본문)
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .apply(print())
            .bodyText()
            .isEqualTo(poll.getPublicId().value());
    }

    @Test
    void 오프셋이_있는_마감_시각은_KST_시각으로_바꿔_서비스에_넘긴다() {
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L))).willReturn(투표를_만든다(null));

        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"question": "질문", "category": "ETC", "options": ["가", "나"],
                 "closesAt": "2099-10-19T14:59:00Z"}
                """)
            .assertThat()
            .hasStatus(HttpStatus.CREATED);

        var captor = ArgumentCaptor.forClass(NewPoll.class);
        verify(pollCommandService).createPoll(captor.capture(), eq(1L));
        assertThat(captor.getValue().closesAt()).isEqualTo(LocalDateTime.of(2099, 10, 19, 23, 59));
    }

    @Test
    void X_Member_Id_헤더의_값이_서비스에_memberId로_전달된다() {
        given(pollCommandService.createPoll(any(NewPoll.class), eq(7L))).willReturn(투표를_만든다(null));

        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "7")
            .contentType(MediaType.APPLICATION_JSON)
            .content(본문)
            .assertThat()
            .hasStatus(HttpStatus.CREATED);

        verify(pollCommandService).createPoll(any(NewPoll.class), eq(7L));
    }

    @Test
    void 임시_신원이_없으면_401_UNAUTHENTICATED이고_서비스를_부르지_않는다() {
        mvcTester.post()
            .uri("/api/v1/polls")
            .contentType(MediaType.APPLICATION_JSON)
            .content(본문)
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .apply(print())
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("UNAUTHENTICATED"));

        verifyNoInteractions(pollCommandService);
    }

    @Test
    void 알_수_없는_요청_필드는_무시하고_생성한다() {
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L))).willReturn(투표를_만든다(null));

        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"question": "질문", "category": "ETC", "options": ["가", "나"],
                 "imageUrl": "https://example.com/a.png", "link": "https://example.com"}
                """)
            .assertThat()
            .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void 깨진_JSON은_400_MALFORMED_REQUEST다() {
        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\": ")
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("MALFORMED_REQUEST"));
    }

    @Test
    void 필수_필드가_없으면_400_VALIDATION_FAILED이고_서비스를_부르지_않는다() {
        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .apply(print())
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("VALIDATION_FAILED"))
            .hasPathSatisfying("$.errors", v -> v.assertThat().isNotNull());

        verifyNoInteractions(pollCommandService);
    }

    @Test
    void 도메인_검증_실패는_400_VALIDATION_FAILED이고_errors에_필드와_사유를_담는다() {
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L)))
            .willThrow(AuddumException.validation("question", "질문은 1~500자여야 합니다."));

        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(본문)
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .apply(print())
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("VALIDATION_FAILED"))
            .hasPathSatisfying("$.errors[0].field", v -> v.assertThat().isEqualTo("question"))
            .hasPathSatisfying("$.errors[0].reason", v -> v.assertThat().isEqualTo("질문은 1~500자여야 합니다."));
    }

    @Test
    void 허용되지_않은_카테고리는_400이고_서비스를_부르지_않는다() {
        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"question": "질문", "category": "NOPE", "options": ["가", "나"]}
                """)
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(pollCommandService);
    }

    @Test
    void 과거_마감_시각은_400_VALIDATION_FAILED이고_closesAt을_errors에_담는다() {
        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"question": "질문", "category": "ETC", "options": ["가", "나"],
                 "closesAt": "2000-01-01T00:00:00+09:00"}
                """)
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("VALIDATION_FAILED"))
            .hasPathSatisfying("$.errors[0].field", v -> v.assertThat().isEqualTo("closesAt"));

        verifyNoInteractions(pollCommandService);
    }

    @Test
    void 선택지가_한_개이면_400_VALIDATION_FAILED이고_서비스를_부르지_않는다() {
        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"question": "질문", "category": "ETC", "options": ["가"]}
                """)
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("VALIDATION_FAILED"));

        verifyNoInteractions(pollCommandService);
    }

    @Test
    void 서비스가_POLL_RATE_LIMITED를_던지면_429다() {
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L)))
            .willThrow(new AuddumException(PollErrorCode.POLL_RATE_LIMITED));

        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(본문)
            .assertThat()
            .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .apply(print())
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("POLL_RATE_LIMITED"))
            .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo(429))
            .hasPathSatisfying("$.instance", v -> v.assertThat().isEqualTo("/api/v1/polls"));
    }

    @Test
    void 서비스가_DUPLICATE_POLL을_던지면_409다() {
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L)))
            .willThrow(new AuddumException(PollErrorCode.DUPLICATE_POLL));

        mvcTester.post()
            .uri("/api/v1/polls")
            .header("X-Member-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(본문)
            .assertThat()
            .hasStatus(HttpStatus.CONFLICT)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .apply(print())
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("DUPLICATE_POLL"));
    }

    private Poll 투표를_만든다(LocalDateTime closesAt) {
        return Poll.create(1L, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", Category.SOME,
            List.of("관심 없음", "그냥 바쁜 것", "더 지켜봐야 함"), closesAt, LocalDateTime.now());
    }

}
