package org.kwakmunsu.auddum.vote.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.vote.api.AnonCookies.ANON_COOKIE;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.anonymous;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.choice;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.noCredential;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.WebApiTestSupport;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.support.error.ErrorCode;
import org.kwakmunsu.auddum.vote.application.dto.CastResult;
import org.kwakmunsu.auddum.vote.application.dto.OptionTally;
import org.kwakmunsu.auddum.vote.application.dto.PollTally;
import org.kwakmunsu.auddum.vote.application.dto.VoterCredential;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * POST /api/v1/polls/{pollId}/votes 웹 계층(서비스는 목). API-명세 §4, §7, 인증-중복-참여 §2.
 * 쿠키는 컨트롤러가 @CookieValue로 받아 서비스에 넘기고, 새로 발급했을 때만 Set-Cookie를 붙인다.
 */
class VoteWebApiTest extends WebApiTestSupport {

    private static final String POLL_ID = "aB3dE5fG7hJk";
    private static final String URI = "/api/v1/polls/" + POLL_ID + "/votes";
    private static final String ISSUED_COOKIE = "v1.0b9a3c3e-6c1e-4f55-9a43-2f7e3b6d9a10.c2lnbmF0dXJl";
    private static final long ONE_YEAR_SECONDS = 365L * 24 * 60 * 60;

    // ---- 상태 코드와 본문 ----

    @Test
    void 참여는_201과_myChoice_result를_내린다() {
        given(voteCommandService.castVote(eq(choice(POLL_ID, 2)), argThat(x -> Objects.equals(x.memberId(), 7L))))
            .willReturn(castResult(null));

        mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 2}")
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .hasPathSatisfying("$.myChoice", v -> v.assertThat().isEqualTo(2))
            .hasPathSatisfying("$.result.total", v -> v.assertThat().isEqualTo(42))
            .hasPathSatisfying("$.result.options[0].position", v -> v.assertThat().isEqualTo(1))
            .hasPathSatisfying("$.result.options[0].count", v -> v.assertThat().isEqualTo(10))
            .hasPathSatisfying("$.result.options[1].position", v -> v.assertThat().isEqualTo(2))
            .hasPathSatisfying("$.result.options[1].count", v -> v.assertThat().isEqualTo(25))
            .hasPathSatisfying("$.result.options[2].count", v -> v.assertThat().isEqualTo(7));
    }

    @Test
    void 응답_본문에_내부_식별자와_득표율은_없다() {
        given(voteCommandService.castVote(eq(choice(POLL_ID, 2)), argThat(x -> Objects.equals(x.memberId(), 7L))))
            .willReturn(castResult(null));

        mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 2}")
            .assertThat()
            .bodyJson()
            .doesNotHavePath("$.result.options[0].id")
            .doesNotHavePath("$.result.options[0].optionId")
            .doesNotHavePath("$.result.options[0].ratio")
            .doesNotHavePath("$.result.options[0].percent");
    }

    // ---- 신원 입력 ----

    @Test
    void 로그인한_회원의_ID와_경로의_공개_ID_position을_서비스에_넘긴다() {
        given(voteCommandService.castVote(argThat(x -> x.position() == 2), any(VoterCredential.class)))
            .willReturn(castResult(null));

        mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 2}")
            .assertThat()
            .hasStatus(HttpStatus.CREATED);

        verify(voteCommandService).castVote(eq(choice(POLL_ID, 2)), argThat(x -> Objects.equals(x.memberId(), 7L)));
    }

    @Test
    void 로그인하지_않아도_참여할_수_있고_회원_ID는_null로_넘긴다() {
        given(voteCommandService.castVote(eq(choice(POLL_ID, 1)), argThat(x -> x.memberId() == null)))
            .willReturn(castResult(ISSUED_COOKIE));

        mvcTester.post()
            .uri(URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .assertThat()
            .hasStatus(HttpStatus.CREATED);

        verify(voteCommandService).castVote(eq(choice(POLL_ID, 1)), eq(noCredential()));
    }

    @Test
    void auddum_anon_쿠키_값을_그대로_서비스에_넘긴다() {
        given(voteCommandService.castVote(argThat(x -> x.position() == 1), any(VoterCredential.class)))
            .willReturn(castResult(null));

        mvcTester.post()
            .uri(URI)
            .cookie(new Cookie(ANON_COOKIE, ISSUED_COOKIE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .assertThat()
            .hasStatus(HttpStatus.CREATED);

        verify(voteCommandService).castVote(eq(choice(POLL_ID, 1)), eq(anonymous(ISSUED_COOKIE)));
    }

    // ---- Set-Cookie (인증-중복-참여 §2) ----

    @Test
    void 서비스가_새_쿠키를_발급하면_Set_Cookie에_속성과_함께_내린다() {
        given(voteCommandService.castVote(eq(choice(POLL_ID, 1)), eq(noCredential())))
            .willReturn(castResult(ISSUED_COOKIE));

        var result = mvcTester.post()
            .uri(URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(1);
        assertThat(cookies.get(0)).startsWith(ANON_COOKIE + "=" + ISSUED_COOKIE)
            .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/;", "Max-Age=" + ONE_YEAR_SECONDS);
    }

    @Test
    void 위조_쿠키를_보내도_서비스가_새로_발급한_값으로_Set_Cookie를_내리고_오류가_아니다() {
        var forged = "v1.0b9a3c3e-6c1e-4f55-9a43-2f7e3b6d9a10.AAAA";
        given(voteCommandService.castVote(eq(choice(POLL_ID, 1)), eq(anonymous(forged))))
            .willReturn(castResult(ISSUED_COOKIE));

        var result = mvcTester.post()
            .uri(URI)
            .cookie(new Cookie(ANON_COOKIE, forged))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .anyMatch(cookie -> cookie.startsWith(ANON_COOKIE + "=" + ISSUED_COOKIE));
    }

    @Test
    void 유효한_쿠키로_참여하면_Set_Cookie를_내리지_않는다() {
        given(voteCommandService.castVote(eq(choice(POLL_ID, 1)), eq(anonymous(ISSUED_COOKIE))))
            .willReturn(castResult(null));

        var result = mvcTester.post()
            .uri(URI)
            .cookie(new Cookie(ANON_COOKIE, ISSUED_COOKIE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 회원이_참여하면_쿠키가_와도_Set_Cookie를_내리지_않는다() {
        given(voteCommandService.castVote(eq(choice(POLL_ID, 1)), argThat(x -> Objects.equals(x.memberId(), 7L))))
            .willReturn(castResult(null));

        var result = mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .cookie(new Cookie(ANON_COOKIE, ISSUED_COOKIE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    // ---- 오류 코드 매핑 (API-명세 §7) ----

    @Test
    void 없는_투표는_404_POLL_NOT_FOUND다() {
        assertErrorMapping(PollErrorCode.POLL_NOT_FOUND, HttpStatus.NOT_FOUND, "POLL_NOT_FOUND");
    }

    @Test
    void 공개_ID_형식_오류는_400_MALFORMED_REQUEST다() {
        assertErrorMapping(DefaultErrorCode.MALFORMED_REQUEST, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    }

    @Test
    void 정지_회원은_403_MEMBER_SUSPENDED다() {
        assertErrorMapping(MemberErrorCode.MEMBER_SUSPENDED, HttpStatus.FORBIDDEN, "MEMBER_SUSPENDED");
    }

    @Test
    void 이미_참여했으면_409_ALREADY_VOTED다() {
        assertErrorMapping(VoteErrorCode.ALREADY_VOTED, HttpStatus.CONFLICT, "ALREADY_VOTED");
    }

    @Test
    void 마감된_투표는_409_POLL_CLOSED다() {
        assertErrorMapping(PollErrorCode.POLL_CLOSED, HttpStatus.CONFLICT, "POLL_CLOSED");
    }

    @Test
    void 존재하지_않는_선택지는_400_INVALID_OPTION이다() {
        assertErrorMapping(PollErrorCode.INVALID_OPTION, HttpStatus.BAD_REQUEST, "INVALID_OPTION");
    }

    @Test
    void 탈퇴한_회원은_401_UNAUTHENTICATED다() {
        assertErrorMapping(DefaultErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    @Test
    void 서비스_내부_오류는_500_INTERNAL_ERROR이고_Set_Cookie를_내리지_않는다() {
        given(voteCommandService.castVote(argThat(x -> x.position() == 1), any(VoterCredential.class)))
            .willThrow(new IllegalStateException("db down"));

        var result = mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("INTERNAL_ERROR"));
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    // ---- 오류로 끝나면 새 쿠키도 내리지 않는다 (인증-중복-참여 §2) ----

    @Test
    void 쿠키_없는_비회원이_INVALID_OPTION이면_400이고_Set_Cookie를_내리지_않는다() {
        assertAnonymousErrorWithoutCookie(PollErrorCode.INVALID_OPTION, HttpStatus.BAD_REQUEST);
    }

    @Test
    void 쿠키_없는_비회원이_POLL_CLOSED이면_409이고_Set_Cookie를_내리지_않는다() {
        assertAnonymousErrorWithoutCookie(PollErrorCode.POLL_CLOSED, HttpStatus.CONFLICT);
    }

    @Test
    void 쿠키_없는_비회원이_ALREADY_VOTED이면_409이고_Set_Cookie를_내리지_않는다() {
        assertAnonymousErrorWithoutCookie(VoteErrorCode.ALREADY_VOTED, HttpStatus.CONFLICT);
    }

    @Test
    void 쿠키_없는_비회원이_POLL_NOT_FOUND이면_404이고_Set_Cookie를_내리지_않는다() {
        assertAnonymousErrorWithoutCookie(PollErrorCode.POLL_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    // ---- 요청 본문 ----

    @Test
    void position이_없으면_400_VALIDATION_FAILED이고_서비스를_부르지_않는다() {
        mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("VALIDATION_FAILED"));

        verifyNoInteractions(voteCommandService);
    }

    @Test
    void 깨진_JSON은_400_MALFORMED_REQUEST다() {
        mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": ")
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("MALFORMED_REQUEST"));

        verifyNoInteractions(voteCommandService);
    }

    @Test
    void position이_숫자가_아니면_400_MALFORMED_REQUEST다() {
        mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": \"둘\"}")
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("MALFORMED_REQUEST"));

        verifyNoInteractions(voteCommandService);
    }

    @Test
    void 알_수_없는_요청_필드는_무시하고_참여한다() {
        given(voteCommandService.castVote(eq(choice(POLL_ID, 2)), argThat(x -> Objects.equals(x.memberId(), 7L))))
            .willReturn(castResult(null));

        mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 2, \"optionId\": 999, \"memberId\": 1}")
            .assertThat()
            .hasStatus(HttpStatus.CREATED);
    }

    private void assertErrorMapping(ErrorCode errorCode, HttpStatus status, String code) {
        given(voteCommandService.castVote(argThat(x -> x.position() == 1), any(VoterCredential.class)))
            .willThrow(new AuddumException(errorCode));

        var result = mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(status)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo(code));
    }

    private void assertAnonymousErrorWithoutCookie(ErrorCode errorCode, HttpStatus status) {
        given(voteCommandService.castVote(argThat(x -> x.position() == 1), eq(noCredential())))
            .willThrow(new AuddumException(errorCode));

        var result = mvcTester.post()
            .uri(URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(status);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    private CastResult castResult(String newCookieValue) {
        var tally = new PollTally(42, List.of(new OptionTally(1, 10), new OptionTally(2, 25), new OptionTally(3, 7)));

        return new CastResult(2, tally, newCookieValue);
    }

}
