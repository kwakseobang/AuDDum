package org.kwakmunsu.auddum.poll.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.vote.api.AnonCookies.ANON_COOKIE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kwakmunsu.auddum.WebApiTestSupport;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.poll.application.dto.OptionTally;
import org.kwakmunsu.auddum.poll.application.dto.PollDetail;
import org.kwakmunsu.auddum.poll.application.dto.PollTally;
import org.kwakmunsu.auddum.poll.application.dto.Viewer;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * GET /api/v1/polls/{pollId} 의 참여 정보(myChoice, result, participantCount) 웹 계층(서비스는 목).
 * API-명세 §3·§4 결과 공개 규칙, 인증-중복-참여 §2 읽기 전용 경로.
 * 공개 여부 판단은 서비스의 몫이라 여기서는 서비스가 준 값을 그대로 내리는지, 신원(회원 ID, 쿠키)을 서비스에 넘기는지만 본다.
 * 조회가 Set-Cookie를 내리지 않는 보장은 서비스가 목이면 어떤 구현에서도 통과하므로 PollDetailChainTest(실제 체인)가 한다.
 * 컨트롤러 슬라이스라서 응답 모양과 신원 전달만 본다. 열람 규칙·status·오류 응답은 PollDetailChainTest, HiddenPollViewChainTest와 서비스 테스트가 확인한다.
 */
class PollDetailWebApiTest extends WebApiTestSupport {

    private static final String ID = "aB3dE5fG7hJk";
    private static final String URI = "/api/v1/polls/" + ID;
    private static final LocalDateTime CLOSES_AT = LocalDateTime.of(2099, 10, 19, 23, 59);

    // ---- 응답 본문 ----

    @Test
    void 참여_후_상세는_myChoice와_result를_내린다() {
        given(pollQueryService.getPollDetail(eq(ID), eq(member(7L))))
            .willReturn(detail(2, tally()));

        mvcTester.get()
            .uri(URI)
            .with(loginAs(7L))
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .hasPathSatisfying("$.participantCount", v -> v.assertThat().isEqualTo(42))
            .hasPathSatisfying("$.myChoice", v -> v.assertThat().isEqualTo(2))
            .hasPathSatisfying("$.result.total", v -> v.assertThat().isEqualTo(42))
            .hasPathSatisfying("$.result.options[0].position", v -> v.assertThat().isEqualTo(1))
            .hasPathSatisfying("$.result.options[0].count", v -> v.assertThat().isEqualTo(10))
            .hasPathSatisfying("$.result.options[1].position", v -> v.assertThat().isEqualTo(2))
            .hasPathSatisfying("$.result.options[1].count", v -> v.assertThat().isEqualTo(25))
            .hasPathSatisfying("$.result.options[2].position", v -> v.assertThat().isEqualTo(3))
            .hasPathSatisfying("$.result.options[2].count", v -> v.assertThat().isEqualTo(7));
    }

    @Test
    void 참여_전_상세는_result가_null이어도_선택지와_participantCount를_내린다() {
        given(pollQueryService.getPollDetail(eq(ID), eq(noCredential())))
            .willReturn(detail(null, null));

        var body = readBody();

        assertThat(body).containsKeys("myChoice", "result");
        assertThat(body.get("myChoice")).isNull();
        assertThat(body.get("result")).isNull();
        assertThat(body.get("participantCount")).isEqualTo(42);
        assertThat((List<?>) body.get("options")).hasSize(3);
    }

    @Test
    void 마감_후_미참여자에게는_myChoice가_null이고_result가_있다() {
        given(pollQueryService.getPollDetail(eq(ID), eq(noCredential())))
            .willReturn(detail(null, tally()));

        mvcTester.get()
            .uri(URI)
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .hasPathSatisfying("$.myChoice", v -> v.assertThat().isNull())
            .hasPathSatisfying("$.result.total", v -> v.assertThat().isEqualTo(42));
    }

    @Test
    void 득표_0인_선택지도_result에_count_0으로_내린다() {
        var zero = PollTally.of(0, List.of(OptionTally.of(1, 0), OptionTally.of(2, 0), OptionTally.of(3, 0)));
        given(pollQueryService.getPollDetail(eq(ID), eq(noCredential())))
            .willReturn(new PollDetail(poll(), true, 0, null, zero));

        mvcTester.get()
            .uri(URI)
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .hasPathSatisfying("$.participantCount", v -> v.assertThat().isEqualTo(0))
            .hasPathSatisfying("$.result.total", v -> v.assertThat().isEqualTo(0))
            .hasPathSatisfying("$.result.options[2].position", v -> v.assertThat().isEqualTo(3))
            .hasPathSatisfying("$.result.options[2].count", v -> v.assertThat().isEqualTo(0));
    }

    @Test
    void 서비스가_준_closed를_그대로_내린다() {
        given(pollQueryService.getPollDetail(eq(ID), eq(noCredential())))
            .willReturn(new PollDetail(poll(), true, 42, null, tally()));

        mvcTester.get()
            .uri(URI)
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .hasPathSatisfying("$.closed", v -> v.assertThat().isEqualTo(true));
    }

    @Test
    void 응답_키는_명세의_필드만이고_득표율과_내부_식별자는_없다() {
        given(pollQueryService.getPollDetail(eq(ID), eq(member(7L))))
            .willReturn(detail(2, tally()));

        var body = readBody(loginAs(7L));

        assertThat(body.keySet()).containsExactlyInAnyOrder(
            "id", "question", "category", "status", "options", "closesAt", "closed", "participantCount", "myChoice", "result");
        @SuppressWarnings("unchecked")
        var firstOption = ((List<Map<String, Object>>) ((Map<String, Object>) body.get("result")).get("options")).getFirst();
        assertThat(firstOption.keySet()).containsExactlyInAnyOrder("position", "count");
    }

    @Test
    void 상세는_명세의_투표_필드를_내린다() {
        var poll = poll(CLOSES_AT);
        given(pollQueryService.getPollDetail(eq(ID), eq(noCredential())))
            .willReturn(detailOf(poll));

        mvcTester.get()
            .uri(URI)
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .hasPathSatisfying("$.id", v -> v.assertThat().isEqualTo(poll.getPublicId().value()))
            .hasPathSatisfying("$.question", v -> v.assertThat().isEqualTo("읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?"))
            .hasPathSatisfying("$.category", v -> v.assertThat().isEqualTo("SOME"))
            .hasPathSatisfying("$.options[0].position", v -> v.assertThat().isEqualTo(1))
            .hasPathSatisfying("$.options[0].content", v -> v.assertThat().isEqualTo("관심 없음"))
            .hasPathSatisfying("$.options[2].position", v -> v.assertThat().isEqualTo(3))
            .hasPathSatisfying("$.options[2].content", v -> v.assertThat().isEqualTo("더 지켜봐야 함"))
            .hasPathSatisfying("$.closesAt", v -> v.assertThat().isEqualTo("2099-10-19T23:59:00+09:00"))
            .hasPathSatisfying("$.closed", v -> v.assertThat().isEqualTo(false));
    }

    // ---- 신원 입력: 회원 ID, 익명 쿠키 ----

    @ParameterizedTest
    @MethodSource("신원_입력")
    void 회원_ID와_익명_쿠키_값을_해석하지_않고_그대로_서비스에_넘긴다(Long memberId, String cookieValue, Viewer expected) {
        given(pollQueryService.getPollDetail(eq(ID), eq(expected))).willReturn(detail(null, null));

        var request = mvcTester.get().uri(URI);
        if (cookieValue != null) {
            request = request.cookie(new Cookie(ANON_COOKIE, cookieValue));
        }
        if (memberId != null) {
            request = request.with(loginAs(memberId));
        }
        request.assertThat().hasStatus(HttpStatus.OK);

        verify(pollQueryService).getPollDetail(eq(ID), eq(expected));
    }

    static Stream<Arguments> 신원_입력() {
        return Stream.of(
            Arguments.of(null, "v1.cookie-value", new Viewer(null, "v1.cookie-value")),
            Arguments.of(null, null, new Viewer(null, null)),
            Arguments.of(7L, null, new Viewer(7L, null)),
            Arguments.of(null, "", new Viewer(null, ""))
        );
    }

    // ---- 보조 ----

    private static Viewer member(Long memberId) {
        return new Viewer(memberId, null);
    }

    private static Viewer anonymous(String cookieValue) {
        return new Viewer(null, cookieValue);
    }

    private static Viewer noCredential() {
        return new Viewer(null, null);
    }

    private Map<String, Object> readBody() {
        return readBody(request -> request);
    }

    private Map<String, Object> readBody(RequestPostProcessor processor) {
        var result = mvcTester.get().uri(URI).with(processor).exchange();
        assertThat(result).hasStatus(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = jsonMapper.readValue(result.getResponse().getContentAsByteArray(), Map.class);

        return body;
    }

    private PollDetail detail(Integer myChoice, PollTally result) {
        return new PollDetail(poll(), false, 42, myChoice, result);
    }

    private PollTally tally() {
        return PollTally.of(42, List.of(OptionTally.of(1, 10), OptionTally.of(2, 25), OptionTally.of(3, 7)));
    }

    /** 참여 정보가 없는 상세(참여 전, 결과 비공개). 마감 여부는 투표의 마감 시각으로 정한다. */
    private PollDetail detailOf(Poll poll) {
        return new PollDetail(poll, poll.isClosed(LocalDateTime.now(TimeConfig.KST)), 0, null, null);
    }

    private Poll poll() {
        return poll(null);
    }

    private Poll poll(LocalDateTime closesAt) {
        var poll = Poll.create(1L, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", Category.SOME,
            List.of("관심 없음", "그냥 바쁜 것", "더 지켜봐야 함"), closesAt, LocalDateTime.now());
        ReflectionTestUtils.setField(poll, "id", 1L);
        ReflectionTestUtils.setField(poll, "createdAt", LocalDateTime.of(2026, 10, 12, 13, 0));

        return poll;
    }

}
