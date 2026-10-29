package org.kwakmunsu.auddum.poll.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
 * POST /api/v1/polls 웹 계층(서비스는 목). API 명세.
 * 작성자는 로그인한 회원(SecurityContext)에서 읽는다.
 */
class PollWebApiTest extends WebApiTestSupport {

    private static final String REQUEST_BODY = """
        {"question": "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?",
         "category": "SOME",
         "options": ["관심 없음", "그냥 바쁜 것", "더 지켜봐야 함"]}
        """;

    @Test
    void 생성하면_201과_Location을_내리고_본문은_공개_ID_문자열_하나다() {
        var poll = createPoll(null);
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L))).willReturn(poll);

        mvcTester.post()
            .uri("/api/v1/polls")
            .with(loginAs(1L))
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY)
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .hasHeader("Location", "/api/v1/polls/" + poll.getPublicId().value())
            .bodyText()
            .isEqualTo(poll.getPublicId().value());
    }

    @Test
    void 오프셋이_있는_마감_시각은_KST_시각으로_바꿔_서비스에_넘긴다() {
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L))).willReturn(createPoll(null));

        mvcTester.post()
            .uri("/api/v1/polls")
            .with(loginAs(1L))
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
    void 알_수_없는_요청_필드는_무시하고_생성한다() {
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L))).willReturn(createPoll(null));

        mvcTester.post()
            .uri("/api/v1/polls")
            .with(loginAs(1L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"question": "질문", "category": "ETC", "options": ["가", "나"],
                 "imageUrl": "https://example.com/a.png", "link": "https://example.com"}
                """)
            .assertThat()
            .hasStatus(HttpStatus.CREATED);
    }

    @ParameterizedTest
    @MethodSource("생성_오류와_상태")
    void 서비스가_던진_생성_오류는_코드와_맞는_상태로_내린다(PollErrorCode errorCode, HttpStatus status) {
        given(pollCommandService.createPoll(any(NewPoll.class), eq(1L)))
            .willThrow(new AuddumException(errorCode));

        mvcTester.post()
            .uri("/api/v1/polls")
            .with(loginAs(1L))
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY)
            .assertThat()
            .hasStatus(status)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo(errorCode.name()))
            .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo(status.value()))
            .hasPathSatisfying("$.instance", v -> v.assertThat().isEqualTo("/api/v1/polls"));
    }

    static Stream<Arguments> 생성_오류와_상태() {
        return Stream.of(
            Arguments.of(PollErrorCode.POLL_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS),
            Arguments.of(PollErrorCode.DUPLICATE_POLL, HttpStatus.CONFLICT)
        );
    }

    private Poll createPoll(LocalDateTime closesAt) {
        return Poll.create(1L, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", Category.SOME,
            List.of("관심 없음", "그냥 바쁜 것", "더 지켜봐야 함"), closesAt, LocalDateTime.now());
    }

}
