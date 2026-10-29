package org.kwakmunsu.auddum.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.report.ReportFixture.handled;
import static org.kwakmunsu.auddum.report.ReportFixture.newDeletedPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newHiddenPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newReport;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.DataIntegrationTestSupport;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.domain.ReportReasonCode;
import org.kwakmunsu.auddum.report.domain.ReportStatus;
import org.kwakmunsu.auddum.report.infrastructure.ReportJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/v1/admin/reports, GET /api/v1/admin/reports/{reportId} 를 실제 필터 체인·서비스·DB(H2)로 확인한다.
 * 기준은 페이징·오류 응답, 관리자 판정, 관리자 권한·OP-01,
 * 신고(Report). 구현 전에 쓰였으므로 서비스·저장소 이름에는 기대지 않고 HTTP와 ReportFixture로만 확인한다.
 * 이 PR에는 AdminAction이 없어 actions는 항상 빈 배열이다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class AdminReportChainTest extends DataIntegrationTestSupport {

    static final String LIST_URI = "/api/v1/admin/reports";

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;
    final ReportJpaRepository reportJpaRepository;

    // ---- 권한 (인증·인가 실패의 구분) ----

    @Test
    void 관리자는_목록을_조회하면_200이다() {
        var admin = saveMember(Role.ADMIN);

        var result = get(LIST_URI, admin);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.items").asList().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.nextCursor").isNull();
    }

    @Test
    void 일반_회원이_목록을_조회하면_403_FORBIDDEN이다() {
        var member = saveMember(Role.MEMBER);

        var result = get(LIST_URI, member);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void 정지된_관리자가_목록을_조회하면_MEMBER_SUSPENDED가_아니라_403_FORBIDDEN이다() {
        var admin = saveMember(Role.ADMIN);
        suspend(admin);

        var result = get(LIST_URI, admin);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void 미로그인이_목록을_조회하면_401_UNAUTHENTICATED다() {
        var result = mvcTester.get().uri(LIST_URI).exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void 일반_회원이_상세를_조회하면_신고가_있어도_403_FORBIDDEN이다() {
        var report = saveReport(savePoll(newPoll(saveMember())));

        var result = get(detailUri(report), saveMember(Role.MEMBER));

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void 일반_회원이_없는_신고_상세를_조회해도_404가_아니라_403_FORBIDDEN이다() {
        var result = get(LIST_URI + "/999999", saveMember(Role.MEMBER));

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void 정지된_관리자가_상세를_조회하면_403_FORBIDDEN이다() {
        var report = saveReport(savePoll(newPoll(saveMember())));
        var admin = saveMember(Role.ADMIN);
        suspend(admin);

        var result = get(detailUri(report), admin);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void 미로그인이_상세를_조회하면_401_UNAUTHENTICATED다() {
        var report = saveReport(savePoll(newPoll(saveMember())));

        var result = mvcTester.get().uri(detailUri(report)).exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    // ---- 목록: status 필터 ----

    @Test
    void status가_없으면_PENDING_신고만_조회한다() {
        var poll = savePoll(newPoll(saveMember()));
        var admin = saveMember(Role.ADMIN);
        var pending = saveReport(poll);
        saveReport(poll, ReportStatus.RESOLVED, admin);
        saveReport(poll, ReportStatus.DISMISSED, admin);

        var result = get(LIST_URI, admin);

        assertThat(result).hasStatus(200);
        assertThat(longs(result, "$.items[*].id")).containsExactly(pending.getId());
        assertThat(strings(result, "$.items[*].status")).containsExactly("PENDING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RESOLVED", "DISMISSED"})
    void status로_지정한_상태의_신고만_조회한다(String status) {
        var poll = savePoll(newPoll(saveMember()));
        var admin = saveMember(Role.ADMIN);
        saveReport(poll);
        saveReport(poll, ReportStatus.RESOLVED, admin);
        saveReport(poll, ReportStatus.DISMISSED, admin);

        var result = get(LIST_URI + "?status=" + status, admin);

        assertThat(result).hasStatus(200);
        assertThat(strings(result, "$.items[*].status")).containsExactly(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "pending", "ALL", ""})
    void 허용되지_않는_status는_400_MALFORMED_REQUEST다(String status) {
        var result = get(LIST_URI + "?status=" + status, saveMember(Role.ADMIN));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    // ---- 목록: 항목 필드 ----

    @Test
    void 목록_항목은_id_poll_reason_status_createdAt_pollReportCount를_담고_내부_투표_ID와_신고자는_담지_않는다() {
        var poll = savePoll(newPoll(saveMember()));
        var report = saveReport(poll, ReportReasonCode.ABUSE_HATE, null);

        var result = get(LIST_URI, saveMember(Role.ADMIN));

        assertThat(result).hasStatus(200);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.items[0].poll.id").isEqualTo(poll.getPublicId().value());
        json.extractingPath("$.items[0].poll.question").isEqualTo(poll.getQuestion().value());
        json.extractingPath("$.items[0].poll.status").isEqualTo("PUBLIC");
        json.extractingPath("$.items[0].reason").isEqualTo("ABUSE_HATE");
        json.extractingPath("$.items[0].status").isEqualTo("PENDING");
        json.extractingPath("$.items[0].createdAt").isNotNull();
        json.extractingPath("$.items[0].pollReportCount").isEqualTo(1);
        assertThat(longs(result, "$.items[*].id")).containsExactly(report.getId());
        var body = body(result);
        assertThat(body).doesNotContain("reporter").doesNotContain("anonId");
    }

    @Test
    void createdAt은_KST_오프셋이_붙은_ISO_8601이다() {
        saveReport(savePoll(newPoll(saveMember())));

        var result = get(LIST_URI, saveMember(Role.ADMIN));

        assertThat(strings(result, "$.items[0].createdAt").get(0)).endsWith("+09:00");
    }

    @Test
    void pollReportCount는_같은_투표에_쌓인_신고_수이고_처리된_신고도_센다() {
        var admin = saveMember(Role.ADMIN);
        var crowded = savePoll(newPoll(saveMember()));
        var single = savePoll(newPoll(saveMember()));
        var first = saveReport(crowded);
        var second = saveReport(crowded);
        saveReport(crowded, ReportStatus.RESOLVED, admin);
        var alone = saveReport(single);

        var result = get(LIST_URI, admin);

        assertThat(longs(result, "$.items[*].id")).containsExactly(first.getId(), second.getId(), alone.getId());
        assertThat(ints(result, "$.items[*].pollReportCount")).containsExactly(3, 3, 1);
    }

    // ---- 목록: 숨김·삭제 투표 ----

    @Test
    void 숨김_투표에_대한_신고도_목록에서_조회되고_poll_status가_HIDDEN이다() {
        var hidden = savePoll(newHiddenPoll(saveMember()));
        var report = saveReport(hidden);

        var result = get(LIST_URI, saveMember(Role.ADMIN));

        assertThat(result).hasStatus(200);
        assertThat(longs(result, "$.items[*].id")).containsExactly(report.getId());
        assertThat(strings(result, "$.items[*].poll.status")).containsExactly("HIDDEN");
        assertThat(strings(result, "$.items[*].poll.id")).containsExactly(hidden.getPublicId().value());
    }

    @Test
    void 삭제된_투표에_대한_신고도_목록에서_조회되고_poll_status가_DELETED다() {
        var deleted = savePoll(newDeletedPoll(saveMember()));
        var report = saveReport(deleted);

        var result = get(LIST_URI, saveMember(Role.ADMIN));

        assertThat(result).hasStatus(200);
        assertThat(longs(result, "$.items[*].id")).containsExactly(report.getId());
        assertThat(strings(result, "$.items[*].poll.status")).containsExactly("DELETED");
    }

    // ---- 목록: 접수 순 정렬과 커서 ----

    @Test
    void 목록은_접수_순_id_오름차순이다() {
        var poll = savePoll(newPoll(saveMember()));
        var otherPoll = savePoll(newPoll(saveMember()));
        var first = saveReport(poll);
        var second = saveReport(otherPoll);
        var third = saveReport(poll);

        var result = get(LIST_URI, saveMember(Role.ADMIN));

        assertThat(longs(result, "$.items[*].id")).containsExactly(first.getId(), second.getId(), third.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RESOLVED", "DISMISSED"})
    void 정렬은_상태와_상관없이_접수_순이다(String status) {
        var poll = savePoll(newPoll(saveMember()));
        var admin = saveMember(Role.ADMIN);
        var reportStatus = ReportStatus.valueOf(status);
        var first = saveReport(poll, reportStatus, admin);
        var second = saveReport(poll, reportStatus, admin);
        var third = saveReport(poll, reportStatus, admin);

        var result = get(LIST_URI + "?status=" + status, admin);

        assertThat(longs(result, "$.items[*].id")).containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void 커서로_이어_조회하면_중복과_누락_없이_접수_순으로_이어지고_마지막_nextCursor는_null이다() {
        var poll = savePoll(newPoll(saveMember()));
        var admin = saveMember(Role.ADMIN);
        var ids = saveReports(poll, 5);

        var page1 = get(LIST_URI + "?size=2", admin);
        var cursor1 = nextCursor(page1);
        var page2 = get(LIST_URI + "?size=2&cursor=" + cursor1, admin);
        var cursor2 = nextCursor(page2);
        var page3 = get(LIST_URI + "?size=2&cursor=" + cursor2, admin);

        assertThat(longs(page1, "$.items[*].id")).containsExactly(ids.get(0), ids.get(1));
        assertThat(cursor1).isNotBlank();
        assertThat(longs(page2, "$.items[*].id")).containsExactly(ids.get(2), ids.get(3));
        assertThat(cursor2).isNotBlank();
        assertThat(longs(page3, "$.items[*].id")).containsExactly(ids.get(4));
        assertThat(page3).bodyJson().extractingPath("$.nextCursor").isNull();
    }

    @Test
    void 정확히_size개로_끝나면_nextCursor는_null이다() {
        var poll = savePoll(newPoll(saveMember()));
        var admin = saveMember(Role.ADMIN);
        saveReports(poll, 2);

        var result = get(LIST_URI + "?size=2", admin);

        assertThat(ints(result, "$.items[*].id")).hasSize(2);
        assertThat(result).bodyJson().extractingPath("$.nextCursor").isNull();
    }

    @Test
    void size가_없으면_기본_20개를_내린다() {
        var poll = savePoll(newPoll(saveMember()));
        saveReports(poll, 21);

        var result = get(LIST_URI, saveMember(Role.ADMIN));

        assertThat(ints(result, "$.items[*].id")).hasSize(20);
        assertThat(nextCursor(result)).isNotBlank();
    }

    @Test
    void size가_50을_넘으면_50으로_보정한다() {
        var poll = savePoll(newPoll(saveMember()));
        saveReports(poll, 51);

        var result = get(LIST_URI + "?size=100", saveMember(Role.ADMIN));

        assertThat(result).hasStatus(200);
        assertThat(ints(result, "$.items[*].id")).hasSize(50);
        assertThat(nextCursor(result)).isNotBlank();
    }

    @Test
    void size가_50이면_50개를_내린다() {
        var poll = savePoll(newPoll(saveMember()));
        saveReports(poll, 51);

        var result = get(LIST_URI + "?size=50", saveMember(Role.ADMIN));

        assertThat(ints(result, "$.items[*].id")).hasSize(50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    void size가_1보다_작으면_1로_보정한다(String size) {
        var poll = savePoll(newPoll(saveMember()));
        saveReports(poll, 3);

        var result = get(LIST_URI + "?size=" + size, saveMember(Role.ADMIN));

        assertThat(result).hasStatus(200);
        assertThat(ints(result, "$.items[*].id")).hasSize(1);
    }

    @Test
    void size가_숫자가_아니면_400_MALFORMED_REQUEST다() {
        var result = get(LIST_URI + "?size=abc", saveMember(Role.ADMIN));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!!not-a-cursor", "abc", "eyJ4Ijox"})
    void 형식이_틀린_cursor는_400_MALFORMED_REQUEST다(String cursor) {
        var result = get(LIST_URI + "?cursor=" + cursor, saveMember(Role.ADMIN));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void cursor가_비어_있으면_첫_페이지다() {
        var poll = savePoll(newPoll(saveMember()));
        var admin = saveMember(Role.ADMIN);
        var ids = saveReports(poll, 2);

        var result = get(LIST_URI + "?cursor=", admin);

        assertThat(result).hasStatus(200);
        assertThat(longs(result, "$.items[*].id")).containsExactly(ids.get(0), ids.get(1));
    }

    // ---- 상세 ----

    @Test
    void 상세는_신고_내용_대상_투표_다른_신고_빈_조치_이력을_담는다() {
        var author = saveMember();
        var poll = savePoll(newPoll(author));
        var report = saveReport(poll, ReportReasonCode.ETC, "특정인을 지목하는 내용입니다.");

        var result = get(detailUri(report), saveMember(Role.ADMIN));

        assertThat(result).hasStatus(200);
        var json = assertThat(result).bodyJson();
        assertThat(longs(result, "$.id")).containsExactly(report.getId());
        json.extractingPath("$.reason").isEqualTo("ETC");
        json.extractingPath("$.detail").isEqualTo("특정인을 지목하는 내용입니다.");
        json.extractingPath("$.status").isEqualTo("PENDING");
        json.extractingPath("$.createdAt").isNotNull();
        json.extractingPath("$.poll.id").isEqualTo(poll.getPublicId().value());
        json.extractingPath("$.poll.question").isEqualTo(poll.getQuestion().value());
        json.extractingPath("$.poll.status").isEqualTo("PUBLIC");
        assertThat(longs(result, "$.poll.authorId")).containsExactly(author);
        json.extractingPath("$.otherReports").asList().isEmpty();
        json.extractingPath("$.actions").asList().isEmpty();
    }

    @Test
    void 상세는_신고자_정보를_노출하지_않는다() {
        var report = saveReport(savePoll(newPoll(saveMember())));

        var result = get(detailUri(report), saveMember(Role.ADMIN));

        assertThat(result).hasStatus(200);
        assertThat(body(result)).doesNotContainIgnoringCase("reporter").doesNotContain("anonId");
        assertThat(body(result)).doesNotContain(report.getReporter().anonId().toString());
    }

    @Test
    void 처리_전_신고의_handledAt과_handledBy는_null이다() {
        var report = saveReport(savePoll(newPoll(saveMember())));

        var result = get(detailUri(report), saveMember(Role.ADMIN));

        assertThat(result).bodyJson().extractingPath("$.handledAt").isNull();
        assertThat(result).bodyJson().extractingPath("$.handledBy").isNull();
    }

    @Test
    void 처리된_신고의_상세는_status와_handledAt_handledBy를_담는다() {
        var admin = saveMember(Role.ADMIN);
        var report = saveReport(savePoll(newPoll(saveMember())), ReportStatus.RESOLVED, admin);

        var result = get(detailUri(report), admin);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("RESOLVED");
        assertThat(result).bodyJson().extractingPath("$.handledAt").isNotNull();
        assertThat(longs(result, "$.handledBy")).containsExactly(admin);
    }

    @Test
    void ETC가_아닌_신고의_detail은_담겨_온_값이_없으면_null이다() {
        var report = saveReport(savePoll(newPoll(saveMember())), ReportReasonCode.SPAM, null);

        var result = get(detailUri(report), saveMember(Role.ADMIN));

        assertThat(result).bodyJson().extractingPath("$.detail").isNull();
    }

    @Test
    void otherReports는_같은_투표의_자기를_뺀_신고를_접수_순으로_담는다() {
        var poll = savePoll(newPoll(saveMember()));
        var admin = saveMember(Role.ADMIN);
        var first = saveReport(poll, ReportReasonCode.SPAM, null);
        var second = saveReport(poll, ReportReasonCode.ETC, "두 번째 신고");
        var third = saveReport(poll, ReportStatus.DISMISSED, admin);

        var result = get(detailUri(second), admin);

        assertThat(longs(result, "$.otherReports[*].id")).containsExactly(first.getId(), third.getId());
        assertThat(strings(result, "$.otherReports[*].reason")).containsExactly("SPAM", "SPAM");
        assertThat(strings(result, "$.otherReports[*].status")).containsExactly("PENDING", "DISMISSED");
        assertThat(result).bodyJson().extractingPath("$.otherReports[0].createdAt").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.otherReports[0].detail").isNull();
        assertThat(longs(result, "$.id")).containsExactly(second.getId());
    }

    @Test
    void otherReports는_다른_투표의_신고를_담지_않는다() {
        var poll = savePoll(newPoll(saveMember()));
        var report = saveReport(poll);
        saveReport(savePoll(newPoll(saveMember())));

        var result = get(detailUri(report), saveMember(Role.ADMIN));

        assertThat(result).bodyJson().extractingPath("$.otherReports").asList().isEmpty();
    }

    @Test
    void otherReports_항목은_신고자_정보를_노출하지_않는다() {
        var poll = savePoll(newPoll(saveMember()));
        var first = saveReport(poll);
        var second = saveReport(poll);

        var result = get(detailUri(first), saveMember(Role.ADMIN));

        assertThat(result).hasStatus(200);
        assertThat(body(result)).doesNotContain(second.getReporter().anonId().toString());
        assertThat(body(result)).doesNotContainIgnoringCase("reporter");
    }

    @Test
    void otherReports는_접수_순_처음_20건까지만_담는다() {
        var admin = saveMember(Role.ADMIN);
        var poll = savePoll(newPoll(saveMember()));
        var ids = saveReports(poll, 23);

        var result = get("/api/v1/admin/reports/" + ids.getFirst(), admin);

        assertThat(longs(result, "$.otherReports[*].id")).containsExactlyElementsOf(ids.subList(1, 21));
    }

    @Test
    void 조치가_없으면_actions는_빈_배열이다() {
        var admin = saveMember(Role.ADMIN);
        var report = saveReport(savePoll(newPoll(saveMember())), ReportStatus.RESOLVED, admin);

        var result = get(detailUri(report), admin);

        assertThat(result).bodyJson().extractingPath("$.actions").asList().isEmpty();
    }

    @Test
    void 숨김_투표에_대한_신고_상세도_조회되고_poll_status가_HIDDEN이다() {
        var hidden = savePoll(newHiddenPoll(saveMember()));
        var report = saveReport(hidden);

        var result = get(detailUri(report), saveMember(Role.ADMIN));

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.poll.status").isEqualTo("HIDDEN");
        assertThat(result).bodyJson().extractingPath("$.poll.id").isEqualTo(hidden.getPublicId().value());
        assertThat(result).bodyJson().extractingPath("$.poll.question").isEqualTo(hidden.getQuestion().value());
    }

    @Test
    void 삭제된_투표에_대한_신고_상세도_조회되고_poll_status가_DELETED다() {
        var deleted = savePoll(newDeletedPoll(saveMember()));
        var report = saveReport(deleted);

        var result = get(detailUri(report), saveMember(Role.ADMIN));

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.poll.status").isEqualTo("DELETED");
        assertThat(result).bodyJson().extractingPath("$.poll.question").isEqualTo(deleted.getQuestion().value());
    }

    @Test
    void 없는_신고_ID는_404_REPORT_NOT_FOUND다() {
        var result = get(LIST_URI + "/999999", saveMember(Role.ADMIN));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("REPORT_NOT_FOUND");
    }

    @Test
    void 숫자가_아닌_신고_ID는_400_MALFORMED_REQUEST다() {
        var result = get(LIST_URI + "/abc", saveMember(Role.ADMIN));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    // ---- 헬퍼 ----

    private MvcTestResult get(String uri, Long memberId) {
        return mvcTester.get().uri(uri).cookie(accessCookie(memberId)).exchange();
    }

    private String detailUri(Report report) {
        return LIST_URI + "/" + report.getId();
    }

    /** 토큰의 role은 DB 판정에 쓰이지 않으므로 실제 DB 역할과 같게 만든다. */
    private Cookie accessCookie(Long memberId) {
        var role = memberJpaRepository.findById(memberId).orElseThrow().getRole();

        return new Cookie(AuthCookies.ACCESS_COOKIE,
            jwtTokenProvider.createAccessToken(memberId, role, LocalDateTime.now(TimeConfig.KST)));
    }

    private Report saveReport(Poll poll) {
        return reportJpaRepository.saveAndFlush(newReport(poll.getId()));
    }

    private Report saveReport(Poll poll, ReportReasonCode code, String detail) {
        return reportJpaRepository.saveAndFlush(newReport(poll.getId(), code, detail));
    }

    private Report saveReport(Poll poll, ReportStatus status, Long adminId) {
        if (status == ReportStatus.PENDING) {
            return saveReport(poll);
        }

        return reportJpaRepository.saveAndFlush(handled(newReport(poll.getId()), status, adminId));
    }

    private List<Long> saveReports(Poll poll, int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> saveReport(poll).getId())
            .toList();
    }

    private String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private String nextCursor(MvcTestResult result) {
        return JsonPath.read(body(result), "$.nextCursor");
    }

    private List<Long> longs(MvcTestResult result, String path) {
        Object value = JsonPath.read(body(result), path);
        if (value instanceof Number number) {
            return List.of(number.longValue());
        }

        return ((List<?>) value).stream().map(item -> ((Number) item).longValue()).toList();
    }

    private List<Integer> ints(MvcTestResult result, String path) {
        return longs(result, path).stream().map(Long::intValue).toList();
    }

    private List<String> strings(MvcTestResult result, String path) {
        Object value = JsonPath.read(body(result), path);
        if (value instanceof String text) {
            return List.of(text);
        }

        return ((List<?>) value).stream().map(String.class::cast).toList();
    }

}
