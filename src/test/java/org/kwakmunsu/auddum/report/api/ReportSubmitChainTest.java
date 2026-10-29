package org.kwakmunsu.auddum.report.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.report.ReportFixture.ALLOWED_ORIGIN;
import static org.kwakmunsu.auddum.report.ReportFixture.DETAIL_MAX;
import static org.kwakmunsu.auddum.report.ReportFixture.newClosedPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newDeletedPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newHiddenPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.reasonBody;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.DataIntegrationTestSupport;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.web.AnonCookieName;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /api/v1/polls/{pollId}/reports 를 실제 필터 체인·서비스·DB(H2)로 확인한다.
 * 기준은 UC-14, R-REPORT-01, R-REPORT-02, R-REPORT-03, R-REPORT-04, 예외-정책 P-05·P-18·OP-08, 인증-중복-참여 익명 식별 쿠키, ERD report 테이블.
 * 서비스·저장소의 클래스 이름은 docs에 없으므로 HTTP와 네이티브 쿼리로만 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class ReportSubmitChainTest extends DataIntegrationTestSupport {

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;
    final EntityManager entityManager;
    final AnonCookieProvider anonCookieProvider;

    // ---- 정상 접수 (API-명세, 도메인-모델 submit) ----

    @Test
    void 비회원이_처음_신고하면_201이고_본문이_없고_익명_쿠키를_내리며_PENDING으로_저장된다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody("SPAM"));

        assertThat(result).hasStatus(201);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .hasSize(1)
            .allMatch(cookie -> cookie.startsWith(AnonCookieName.VALUE + "="));
        var rows = reports();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("PENDING");
        assertThat(rows.get(0).reason()).isEqualTo("SPAM");
        assertThat(rows.get(0).reporterAnonId()).isNotNull().hasSize(36);
        assertThat(rows.get(0).reporterMemberId()).isNull();
    }

    @Test
    void 로그인한_회원이_신고하면_201이고_회원_ID로_저장되며_쿠키를_내리지_않는다() {
        var poll = savePoll(newPoll(saveMember()));
        var memberId = saveMember();

        var result = post(poll, reasonBody("ABUSE_HATE"), accessCookie(memberId));

        assertThat(result).hasStatus(201);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        var rows = reports();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).reporterMemberId()).isEqualTo(memberId);
        assertThat(rows.get(0).reporterAnonId()).isNull();
        assertThat(rows.get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void 로그인한_회원은_익명_쿠키를_함께_보내도_회원_ID로만_저장된다() {
        var poll = savePoll(newPoll(saveMember()));
        var memberId = saveMember();
        var cookie = issueAnonCookie();

        var result = post(poll, reasonBody("SPAM"), accessCookie(memberId), cookie);

        assertThat(result).hasStatus(201);
        var rows = reports();
        assertThat(rows.get(0).reporterMemberId()).isEqualTo(memberId);
        assertThat(rows.get(0).reporterAnonId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABUSE_HATE", "SEXUAL_HARASSMENT", "PERSONAL_INFO", "SPAM"})
    void ETC가_아닌_사유는_상세_없이_접수된다(String reason) {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody(reason));

        assertThat(result).hasStatus(201);
        assertThat(reports().get(0).reason()).isEqualTo(reason);
    }

    @Test
    void ETC_사유는_상세와_함께_접수되고_상세가_저장된다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody("ETC", "특정인을 지목하는 내용입니다."));

        assertThat(result).hasStatus(201);
        assertThat(reports().get(0).reason()).isEqualTo("ETC");
        assertThat(reports().get(0).detail()).isEqualTo("특정인을 지목하는 내용입니다.");
    }

    @Test
    void 상세의_앞뒤_공백은_다듬지_않고_그대로_저장한다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody("ETC", "  앞뒤 공백  "));

        assertThat(result).hasStatus(201);
        assertThat(reports().get(0).detail()).isEqualTo("  앞뒤 공백  ");
    }

    @Test
    void ETC가_아닌_사유에_담긴_상세도_입력_그대로_저장하고_201이다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody("SPAM", "담겨 온 상세"));

        assertThat(result).hasStatus(201);
        assertThat(reports().get(0).detail()).isEqualTo("담겨 온 상세");
    }

    @Test
    void 마감된_투표도_신고할_수_있어_201이고_PENDING으로_저장된다() {
        var poll = savePoll(newClosedPoll(saveMember()));

        var result = post(poll, reasonBody("SPAM"));

        assertThat(result).hasStatus(201);
        var rows = reports();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void 알_수_없는_요청_필드는_무시하고_201이다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, "{\"reason\": \"SPAM\", \"image\": \"http://x/y.png\"}");

        assertThat(result).hasStatus(201);
    }

    // ---- 노출 상태 불변 (API-명세, 예외-정책 신고 접수된 투표 접근) ----

    @Test
    void 신고가_접수돼도_투표는_PUBLIC으로_남고_조회가_200이다() {
        var poll = savePoll(newPoll(saveMember()));

        post(poll, reasonBody("SPAM"));
        post(poll, reasonBody("SPAM"));
        post(poll, reasonBody("PERSONAL_INFO"), accessCookie(saveMember()));

        assertThat(reports()).hasSize(3);
        assertThat(pollStatus(poll)).isEqualTo("PUBLIC");
        var detail = mvcTester.get().uri("/api/v1/polls/" + poll.getPublicId().value()).exchange();
        assertThat(detail).hasStatus(200);
        assertThat(detail).bodyJson().extractingPath("$.status").isEqualTo("PUBLIC");
    }

    // ---- 중복 신고 (P-18, API-명세) ----

    @Test
    void 받은_쿠키로_같은_투표를_다시_신고하면_409_REPORT_ALREADY_SUBMITTED이고_한_건만_남는다() {
        var poll = savePoll(newPoll(saveMember()));
        var first = post(poll, reasonBody("SPAM"));
        var cookie = first.getResponse().getCookie(AnonCookieName.VALUE);

        var second = post(poll, reasonBody("ABUSE_HATE"), cookie);

        assertThat(second).hasStatus(409);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("REPORT_ALREADY_SUBMITTED");
        assertThat(second.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(reports()).hasSize(1);
        assertThat(reports().get(0).reason()).isEqualTo("SPAM");
    }

    @Test
    void 같은_회원이_같은_투표를_다시_신고하면_409_REPORT_ALREADY_SUBMITTED이고_한_건만_남는다() {
        var poll = savePoll(newPoll(saveMember()));
        var login = accessCookie(saveMember());
        post(poll, reasonBody("SPAM"), login);

        var second = post(poll, reasonBody("SPAM"), login);

        assertThat(second).hasStatus(409);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("REPORT_ALREADY_SUBMITTED");
        assertThat(reports()).hasSize(1);
    }

    @Test
    void 같은_신고자라도_다른_투표는_각각_신고할_수_있다() {
        var authorId = saveMember();
        var first = savePoll(newPoll(authorId));
        var second = savePoll(newPoll(authorId));
        var cookie = issueAnonCookie();

        var firstResult = post(first, reasonBody("SPAM"), cookie);
        var secondResult = post(second, reasonBody("SPAM"), cookie);

        assertThat(firstResult).hasStatus(201);
        assertThat(secondResult).hasStatus(201);
        assertThat(reports()).hasSize(2);
    }

    @Test
    void 다른_신고자는_같은_투표를_각각_신고할_수_있다() {
        var poll = savePoll(newPoll(saveMember()));

        var anonymousOne = post(poll, reasonBody("SPAM"), issueAnonCookie());
        var anonymousTwo = post(poll, reasonBody("SPAM"), issueAnonCookie());
        var member = post(poll, reasonBody("SPAM"), accessCookie(saveMember()));

        assertThat(anonymousOne).hasStatus(201);
        assertThat(anonymousTwo).hasStatus(201);
        assertThat(member).hasStatus(201);
        assertThat(reports()).hasSize(3);
    }

    // ---- 숨김·삭제·없는 투표 (API-명세, OP-08) ----

    @Test
    void 숨김_투표를_신고하면_404_POLL_NOT_FOUND이고_저장도_쿠키도_없다() {
        var poll = savePoll(newHiddenPoll(saveMember()));

        var result = post(poll, reasonBody("SPAM"));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(reports()).isEmpty();
    }

    @Test
    void 삭제된_투표를_신고하면_404_POLL_NOT_FOUND이고_저장되지_않는다() {
        var poll = savePoll(newDeletedPoll(saveMember()));

        var result = post(poll, reasonBody("SPAM"));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
        assertThat(reports()).isEmpty();
    }

    @Test
    void 숨김_투표는_작성자가_로그인해도_신고할_수_없다() {
        var authorId = saveMember();
        var poll = savePoll(newHiddenPoll(authorId));

        var result = post(poll, reasonBody("SPAM"), accessCookie(authorId));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
        assertThat(reports()).isEmpty();
    }

    @Test
    void 존재하지_않는_투표를_신고하면_404_POLL_NOT_FOUND다() {
        var result = post("aaaaaaaaaaaa", reasonBody("SPAM"));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 형식이_틀린_투표_ID는_400_MALFORMED_REQUEST다() {
        var result = post("short", reasonBody("SPAM"));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    // ---- 정지 회원 (P-05) ----

    @Test
    void 정지_회원이_신고하면_403_MEMBER_SUSPENDED이고_저장되지_않는다() {
        var poll = savePoll(newPoll(saveMember()));
        var suspendedId = saveSuspendedMember();

        var result = post(poll, reasonBody("SPAM"), accessCookie(suspendedId));

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_SUSPENDED");
        assertThat(reports()).isEmpty();
    }

    // ---- 입력 검증 ----

    @Test
    void 허용되지_않는_사유는_400_MALFORMED_REQUEST이고_쿠키도_저장도_없다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody("NOT_A_REASON"));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(reports()).isEmpty();
    }

    @Test
    void 사유가_없으면_400_VALIDATION_FAILED다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, "{}");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(reports()).isEmpty();
    }

    @Test
    void ETC인데_상세가_없으면_400_VALIDATION_FAILED이고_쿠키도_저장도_없다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody("ETC"));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(reports()).isEmpty();
    }

    @Test
    void ETC인데_상세가_null이면_400_VALIDATION_FAILED다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, "{\"reason\": \"ETC\", \"detail\": null}");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(reports()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   ", "\\t", "\\n"})
    void ETC인데_상세가_공백뿐이면_400_VALIDATION_FAILED이고_쿠키도_저장도_없다(String detail) {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody("ETC", detail));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(reports()).isEmpty();
    }

    @Test
    void ETC_상세가_상한_200자면_201이다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody("ETC", "가".repeat(DETAIL_MAX)));

        assertThat(result).hasStatus(201);
        assertThat(reports().get(0).detail()).hasSize(DETAIL_MAX);
    }

    @Test
    void ETC_상세가_201자면_400_VALIDATION_FAILED이고_저장되지_않는다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody("ETC", "가".repeat(DETAIL_MAX + 1)));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(reports()).isEmpty();
    }

    @Test
    void 본문이_JSON이_아니면_400_MALFORMED_REQUEST다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, "{");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    // ---- 익명 쿠키 ----

    @Test
    void 위조된_익명_쿠키로_신고하면_오류_없이_새_쿠키를_발급해_접수한다() {
        var poll = savePoll(newPoll(saveMember()));
        var forged = new Cookie(AnonCookieName.VALUE, "v1.0b9a3c3e-6c1e-4f55-9a43-2f7e3b6d9a10.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        var result = post(poll, reasonBody("SPAM"), forged);

        assertThat(result).hasStatus(201);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .hasSize(1)
            .allMatch(cookie -> cookie.startsWith(AnonCookieName.VALUE + "="))
            .noneMatch(cookie -> cookie.contains("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        assertThat(reports()).hasSize(1);
    }

    @Test
    void 유효한_쿠키로_처음_신고하면_쿠키를_다시_내리지_않는다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = post(poll, reasonBody("SPAM"), issueAnonCookie());

        assertThat(result).hasStatus(201);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    // ---- Origin (인증-중복-참여, API-명세 FORBIDDEN) ----

    @Test
    void Origin_헤더가_없는_신고는_403_FORBIDDEN이고_저장되지_않는다() {
        var poll = savePoll(newPoll(saveMember()));

        var result = mvcTester.post()
            .uri("/api/v1/polls/" + poll.getPublicId().value() + "/reports")
            .contentType(MediaType.APPLICATION_JSON)
            .content(reasonBody("SPAM"))
            .exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
        assertThat(reports()).isEmpty();
    }

    // ---- 헬퍼 ----

    private MvcTestResult post(Poll poll, String body, Cookie... cookies) {
        return post(poll.getPublicId().value(), body, cookies);
    }

    private MvcTestResult post(String publicId, String body, Cookie... cookies) {
        var request = mvcTester.post()
            .uri("/api/v1/polls/" + publicId + "/reports")
            .header("Origin", ALLOWED_ORIGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        if (cookies.length > 0) {
            request = request.cookie(cookies);
        }
        return request.exchange();
    }

    private Cookie accessCookie(Long memberId) {
        return new Cookie(AuthCookies.ACCESS_COOKIE,
            jwtTokenProvider.createAccessToken(memberId, Role.MEMBER, LocalDateTime.now(TimeConfig.KST)));
    }

    /** 서버가 서명한 유효한 익명 쿠키. 신고 기록은 만들지 않는다. */
    private Cookie issueAnonCookie() {
        return new Cookie(AnonCookieName.VALUE, anonCookieProvider.issue().value());
    }

    private record ReportRow(String status, String reason, String detail, Long reporterMemberId, String reporterAnonId) {

    }

    private List<ReportRow> reports() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
            "select status, reason, detail, reporter_member_id, reporter_anon_id from report order by id").getResultList();

        return rows.stream()
            .map(row -> new ReportRow((String) row[0], (String) row[1], (String) row[2],
                row[3] == null ? null : ((Number) row[3]).longValue(), (String) row[4]))
            .toList();
    }

    private String pollStatus(Poll poll) {
        return (String) entityManager.createNativeQuery("select status from poll where id = :id")
            .setParameter("id", poll.getId())
            .getSingleResult();
    }

}
