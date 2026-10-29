package org.kwakmunsu.auddum.poll.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.WebApiTestSupport;
import org.kwakmunsu.auddum.poll.application.dto.PollSummary;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.ClosesAt;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * GET /api/v1/polls 목록 웹 계층(서비스는 목). API 명세.
 * 정상 응답의 모양만 본다. 조회 규칙은 PollQueryServiceTest가, 참여 수 집계는 PollListParticipantCountChainTest가 맡는다.
 * GET /api/v1/polls/{pollId} 상세는 PollDetailWebApiTest가 맡는다.
 */
class PollListWebApiTest extends WebApiTestSupport {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 12, 13, 0);
    /** {"id":1}을 URL-safe base64로 감싼 값. 테스트 투표의 내부 id가 1이다. */
    private static final String CURSOR_OF_ID_1 = "eyJpZCI6MX0";
    private static final LocalDateTime CLOSES_AT = LocalDateTime.of(2099, 10, 19, 23, 59);

    // ---- 목록 ----

    @Test
    void 인증_없이_목록을_조회하면_200과_items_nextCursor를_내린다() {
        var poll = createPoll(CLOSES_AT);
        given(pollQueryService.getPollSummaries(null, 20)).willReturn(slice(List.of(summary(poll, 42)), true));

        mvcTester.get()
            .uri("/api/v1/polls")
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .apply(print())
            .bodyJson()
            .hasPathSatisfying("$.items[0].id", v -> v.assertThat().isEqualTo(poll.getPublicId().value()))
            .hasPathSatisfying("$.items[0].question", v -> v.assertThat().isEqualTo("읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?"))
            .hasPathSatisfying("$.items[0].category", v -> v.assertThat().isEqualTo("SOME"))
            .hasPathSatisfying("$.items[0].participantCount", v -> v.assertThat().isEqualTo(42))
            .hasPathSatisfying("$.items[0].closed", v -> v.assertThat().isEqualTo(false))
            .hasPathSatisfying("$.items[0].closesAt", v -> v.assertThat().isEqualTo("2099-10-19T23:59:00+09:00"))
            .hasPathSatisfying("$.items[0].createdAt", v -> v.assertThat().isEqualTo("2026-10-12T13:00:00+09:00"))
            .hasPathSatisfying("$.nextCursor", v -> v.assertThat().isEqualTo(CURSOR_OF_ID_1));
    }

    @Test
    void 목록_항목은_내부_id를_노출하지_않는다() {
        given(pollQueryService.getPollSummaries(null, 20)).willReturn(slice(List.of(summary(createPoll(CLOSES_AT), 0)), false));

        var body = readBody("/api/v1/polls");

        @SuppressWarnings("unchecked")
        var item = ((List<Map<String, Object>>) body.get("items")).getFirst();
        assertThat(item.keySet())
            .containsExactlyInAnyOrder("id", "question", "category", "participantCount", "closed", "closesAt", "createdAt");
    }

    @Test
    void 마감_시각이_없는_투표는_closesAt이_null이고_closed가_false다() {
        given(pollQueryService.getPollSummaries(null, 20)).willReturn(slice(List.of(summary(createPoll(null), 0)), false));

        var body = readBody("/api/v1/polls");

        @SuppressWarnings("unchecked")
        var item = ((List<Map<String, Object>>) body.get("items")).getFirst();
        assertThat(item).containsKey("closesAt");
        assertThat(item.get("closesAt")).isNull();
        assertThat(item.get("closed")).isEqualTo(false);
    }

    @Test
    void 마감된_투표는_목록에서_closed가_true다() {
        given(pollQueryService.getPollSummaries(null, 20)).willReturn(slice(List.of(summary(createClosedPoll(), 0)), false));

        mvcTester.get()
            .uri("/api/v1/polls")
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .hasPathSatisfying("$.items[0].closed", v -> v.assertThat().isEqualTo(true));
    }

    @Test
    void 투표가_없으면_빈_items와_null_nextCursor로_200이다() {
        given(pollQueryService.getPollSummaries(null, 20)).willReturn(slice(List.of(), false));

        var body = readBody("/api/v1/polls");

        assertThat(body.get("items")).isEqualTo(List.of());
        assertThat(body).containsKey("nextCursor");
        assertThat(body.get("nextCursor")).isNull();
    }

    @Test
    void cursor를_해석하고_size를_서비스에_넘긴다() {
        given(pollQueryService.getPollSummaries(5L, 10)).willReturn(slice(List.of(), false));

        mvcTester.get()
            .uri("/api/v1/polls?cursor=eyJpZCI6NX0&size=10")
            .assertThat()
            .hasStatus(HttpStatus.OK);

        verify(pollQueryService).getPollSummaries(5L, 10);
    }

    // ---- 보조 ----

    private PollSummary summary(Poll poll, long participantCount) {
        return PollSummary.of(poll, participantCount);
    }

    private Slice<PollSummary> slice(List<PollSummary> polls, boolean hasNext) {
        return new SliceImpl<>(polls, PageRequest.ofSize(20), hasNext);
    }

    private Map<String, Object> readBody(String uri) {
        var result = mvcTester.get().uri(uri).exchange();
        assertThat(result).hasStatus(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = jsonMapper.readValue(result.getResponse().getContentAsByteArray(), Map.class);

        return body;
    }

    private Poll createPoll(LocalDateTime closesAt) {
        var poll = Poll.create(1L, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", Category.SOME,
            List.of("관심 없음", "그냥 바쁜 것", "더 지켜봐야 함"), closesAt, LocalDateTime.now());
        ReflectionTestUtils.setField(poll, "id", 1L);
        ReflectionTestUtils.setField(poll, "createdAt", CREATED_AT);

        return poll;
    }

    /** 생성 시점에는 과거 마감을 만들 수 없으므로 필드를 직접 과거로 바꾼다. */
    private Poll createClosedPoll() {
        var poll = createPoll(CLOSES_AT);
        ReflectionTestUtils.setField(poll, "closesAt", new ClosesAt(LocalDateTime.of(2000, 1, 1, 0, 0)));

        return poll;
    }

}
