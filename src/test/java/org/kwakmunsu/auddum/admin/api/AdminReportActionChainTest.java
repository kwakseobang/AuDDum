package org.kwakmunsu.auddum.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.report.ReportFixture.ALLOWED_ORIGIN;
import static org.kwakmunsu.auddum.report.ReportFixture.handled;
import static org.kwakmunsu.auddum.report.ReportFixture.newDeletedPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newHiddenPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newPoll;
import static org.kwakmunsu.auddum.report.ReportFixture.newReport;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.DataIntegrationTestSupport;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.domain.ReportStatus;
import org.kwakmunsu.auddum.report.infrastructure.ReportJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 조치 API(신고 기각, 투표 숨김·삭제, 회원 정지)를 실제 필터 체인·서비스·DB(H2)로 확인한다.
 * POST /admin/reports/{id}/dismiss, PATCH /admin/polls/{publicId}/status(HIDDEN·DELETED), POST /admin/members/{id}/suspend.
 * 기준은 API-명세(조치 API 설명), 도메인-모델(Poll.hide, Member.suspend, Report.dismiss, AdminAction), 설계-결정 "조치 API는 별도 기능이다", ERD admin_action.
 * 서비스·저장소 이름에는 기대지 않고 HTTP, 기존 저장소, JdbcTemplate(컬럼 이름은 ERD)로만 확인한다.
 * 같은 테스트 트랜잭션 안에서 부르므로 DB 상태는 엔티티 캐시 대신 JdbcTemplate으로 읽는다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class AdminReportActionChainTest extends DataIntegrationTestSupport {

    static final String API = "/api/v1/admin/";
    static final int REASON_MAX = 200;
    static final String REASON = "특정인 비방 내용 확인";
    static final String MISSING_POLL_ID = "Zz9Zz9Zz9Zz9";

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;
    final ReportJpaRepository reportJpaRepository;
    final JdbcTemplate jdbcTemplate;
    final EntityManager entityManager;

    /** 한 투표(작성자 포함)와 그 투표의 PENDING 신고 하나. */
    record Scene(Long authorId, Poll poll, Report report) {

    }

    // ---- 권한 (API-명세 첫 문단, 인증·인가 구분) ----

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 관리자가_조치하면_204이고_본문이_없다(String action) {
        var scene = scene();

        var result = act(action, scene, saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 일반_회원이_조치하면_403_FORBIDDEN이고_아무것도_바뀌지_않는다(String action) {
        var scene = scene();

        var result = act(action, scene, saveMember(Role.MEMBER), body(REASON));

        assertForbidden(result);
        assertNothingChanged(scene);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 일반_회원이_사유를_빼고_보내면_사유_검증보다_권한이_먼저라_403_FORBIDDEN이다(String action) {
        var scene = scene();

        var result = act(action, scene, saveMember(Role.MEMBER), "{}");

        assertForbidden(result);
        assertNothingChanged(scene);
    }

    @Test
    void 일반_회원이_투표_조치에_상태를_빼고_보내도_권한이_먼저라_403_FORBIDDEN이다() {
        var scene = scene();

        var result = post(uri("hide", scene), saveMember(Role.MEMBER), body(REASON));

        assertForbidden(result);
        assertNothingChanged(scene);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 일반_회원이_사유를_201자로_보내도_권한이_먼저라_403_FORBIDDEN이다(String action) {
        var scene = scene();

        var result = act(action, scene, saveMember(Role.MEMBER), body("가".repeat(REASON_MAX + 1)));

        assertForbidden(result);
        assertNothingChanged(scene);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 일반_회원이_없는_대상에_조치해도_404가_아니라_403_FORBIDDEN이다(String action) {
        var result = post(missingUri(action), saveMember(Role.MEMBER), body(REASON));

        assertForbidden(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 정지된_관리자가_조치하면_403_FORBIDDEN이고_아무것도_바뀌지_않는다(String action) {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);
        suspend(admin);

        var result = act(action, scene, admin, body(REASON));

        assertForbidden(result);
        assertNothingChanged(scene);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 미로그인이_조치하면_401_UNAUTHENTICATED다(String action) {
        var scene = scene();

        var result = request(uri(action, scene))
            .header("Origin", ALLOWED_ORIGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(withStatus(action, body(REASON)))
            .exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
        assertNothingChanged(scene);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 미로그인이_사유를_빼고_보내도_400이_아니라_401_UNAUTHENTICATED다(String action) {
        var scene = scene();

        var result = request(uri(action, scene))
            .header("Origin", ALLOWED_ORIGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(withStatus(action, "{}"))
            .exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
        assertNothingChanged(scene);
    }

    // ---- 사유 검증 (API-명세 "모든 요청은 { reason }이 필수다(공백뿐이면 없는 것, 200자 이하)") ----

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 사유가_없으면_400_VALIDATION_FAILED이고_아무것도_바뀌지_않는다(String action) {
        var scene = scene();

        var result = act(action, scene, saveMember(Role.ADMIN), "{}");

        assertValidationFailed(result);
        assertNothingChanged(scene);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 사유가_공백뿐이면_400_VALIDATION_FAILED이고_아무것도_바뀌지_않는다(String action) {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);

        for (var reason : List.of("", " ", "   ", "\\t", "\\n", " \\t\\n ")) {
            assertValidationFailed(act(action, scene, admin, body(reason)));
        }
        assertNothingChanged(scene);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 사유가_상한인_200자면_204다(String action) {
        var result = act(action, scene(), saveMember(Role.ADMIN), body("가".repeat(REASON_MAX)));

        assertThat(result).hasStatus(204);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 사유가_상한을_넘어_201자면_400_VALIDATION_FAILED이고_아무것도_바뀌지_않는다(String action) {
        var scene = scene();

        var result = act(action, scene, saveMember(Role.ADMIN), body("가".repeat(REASON_MAX + 1)));

        assertValidationFailed(result);
        assertNothingChanged(scene);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismiss", "hide", "delete", "suspend"})
    void 본문이_JSON_형식이_아니면_400_MALFORMED_REQUEST다(String action) {
        var scene = scene();

        var result = act(action, scene, saveMember(Role.ADMIN), "{not json");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
        assertNothingChanged(scene);
    }

    // ---- 대상 식별 (API-명세 각 API의 404·400) ----

    @Test
    void 없는_신고를_기각하면_404_REPORT_NOT_FOUND다() {
        var result = post(missingUri("dismiss"), saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("REPORT_NOT_FOUND");
        assertThat(actionCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hide", "delete"})
    void 없는_투표를_숨기거나_삭제하면_404_POLL_NOT_FOUND다(String action) {
        var result = post(missingUri(action), saveMember(Role.ADMIN), withStatus(action, body(REASON)));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
        assertThat(actionCount()).isZero();
    }

    @Test
    void 없는_회원을_정지하면_404_MEMBER_NOT_FOUND다() {
        var result = post(missingUri("suspend"), saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_FOUND");
        assertThat(actionCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"reports/abc/dismiss", "members/abc/suspend", "polls/short/status",
        "polls/ABCDEFGHIJK!/status", "polls/ABCDEFGHIJKLM/status"})
    void ID_형식이_틀리면_400_MALFORMED_REQUEST다(String path) {
        var content = path.endsWith("/status") ? withStatus("hide", body(REASON)) : body(REASON);
        var result = post(API + path, saveMember(Role.ADMIN), content);

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
        assertThat(actionCount()).isZero();
    }

    // ---- 신고 기각 (API-명세 POST dismiss, 도메인-모델 Report.dismiss) ----

    @Test
    void 기각하면_신고가_DISMISSED이고_투표와_작성자는_그대로이며_처리자와_일시를_기록한다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);

        var result = act("dismiss", scene, admin, body(REASON));

        assertThat(result).hasStatus(204);
        assertThat(reportStatus(scene.report())).isEqualTo("DISMISSED");
        assertThat(reportHandledBy(scene.report())).isEqualTo(admin);
        assertThat(jdbcTemplate.queryForObject("SELECT handled_at FROM report WHERE id = ?", Object.class,
            scene.report().getId())).isNotNull();
        assertThat(pollStatus(scene.poll())).isEqualTo("PUBLIC");
        assertThat(memberStatus(scene.authorId())).isEqualTo("ACTIVE");
    }

    @Test
    void 기각하면_같은_투표의_다른_PENDING_신고도_DISMISSED로_종료한다() {
        var scene = scene();
        var other = saveReport(scene.poll());
        var admin = saveMember(Role.ADMIN);

        act("dismiss", scene, admin, body(REASON));

        assertThat(reportStatus(other)).isEqualTo("DISMISSED");
        assertThat(reportHandledBy(other)).isEqualTo(admin);
        assertThat(pollStatus(scene.poll())).isEqualTo("PUBLIC");
    }

    @Test
    void 기각해도_이미_처리된_같은_투표의_신고와_다른_투표의_PENDING_신고는_그대로다() {
        var scene = scene();
        var resolved = saveHandledReport(scene.poll(), ReportStatus.RESOLVED, saveMember(Role.ADMIN));
        var resolvedBefore = reportRow(resolved);
        var otherPollReport = saveReport(savePoll(newPoll(saveMember())));

        act("dismiss", scene, saveMember(Role.ADMIN), body(REASON));

        assertThat(reportRow(resolved)).isEqualTo(resolvedBefore);
        assertThat(reportStatus(otherPollReport)).isEqualTo("PENDING");
    }

    @Test
    void 삭제된_투표의_신고도_기각할_수_있고_투표는_DELETED_그대로다() {
        var poll = savePoll(newDeletedPoll(saveMember()));
        var report = saveReport(poll);

        var result = act("dismiss", new Scene(poll.getAuthorId(), poll, report), saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(204);
        assertThat(reportStatus(report)).isEqualTo("DISMISSED");
        assertThat(pollStatus(poll)).isEqualTo("DELETED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESOLVED", "DISMISSED"})
    void 이미_처리된_신고를_기각하면_409_REPORT_ALREADY_HANDLED이고_상태와_이력이_변하지_않는다(String status) {
        var poll = savePoll(newPoll(saveMember()));
        var report = saveHandledReport(poll, ReportStatus.valueOf(status), saveMember(Role.ADMIN));
        var before = reportRow(report);

        var result = act("dismiss", new Scene(poll.getAuthorId(), poll, report), saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("REPORT_ALREADY_HANDLED");
        assertThat(reportRow(report)).isEqualTo(before);
        assertThat(actionCount()).isZero();
    }

    @Test
    void 같은_신고를_두_번_기각하면_두_번째는_409이고_이력은_첫_번째_것만_남는다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);
        act("dismiss", scene, admin, body(REASON));

        var result = act("dismiss", scene, admin, body(REASON));

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("REPORT_ALREADY_HANDLED");
        assertThat(actionCount()).isEqualTo(1L);
    }

    @Test
    void 투표를_숨기면서_함께_종료된_신고를_기각하면_409_REPORT_ALREADY_HANDLED다() {
        var scene = scene();
        var other = saveReport(scene.poll());
        var admin = saveMember(Role.ADMIN);
        act("hide", scene, admin, body(REASON));

        var result = act("dismiss", new Scene(scene.authorId(), scene.poll(), other), admin, body(REASON));

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("REPORT_ALREADY_HANDLED");
        assertThat(reportStatus(other)).isEqualTo("RESOLVED");
    }

    // ---- 투표 숨김 (API-명세 PATCH status(HIDDEN), 도메인-모델 Poll.hide) ----

    @Test
    void 숨기면_투표가_HIDDEN이고_그_투표의_PENDING_신고가_모두_RESOLVED로_종료된다() {
        var scene = scene();
        var other = saveReport(scene.poll());
        var admin = saveMember(Role.ADMIN);

        var result = act("hide", scene, admin, body(REASON));

        assertThat(result).hasStatus(204);
        assertThat(pollStatus(scene.poll())).isEqualTo("HIDDEN");
        assertThat(reportStatus(scene.report())).isEqualTo("RESOLVED");
        assertThat(reportStatus(other)).isEqualTo("RESOLVED");
        assertThat(reportHandledBy(scene.report())).isEqualTo(admin);
        assertThat(reportHandledBy(other)).isEqualTo(admin);
        assertThat(jdbcTemplate.queryForObject("SELECT handled_at FROM report WHERE id = ?", Object.class,
            other.getId())).isNotNull();
        assertThat(memberStatus(scene.authorId())).isEqualTo("ACTIVE");
    }

    @Test
    void 숨겨도_이미_처리된_신고와_다른_투표의_신고는_그대로다() {
        var scene = scene();
        var dismissed = saveHandledReport(scene.poll(), ReportStatus.DISMISSED, saveMember(Role.ADMIN));
        var dismissedBefore = reportRow(dismissed);
        var otherPoll = savePoll(newPoll(saveMember()));
        var otherPollReport = saveReport(otherPoll);

        act("hide", scene, saveMember(Role.ADMIN), body(REASON));

        assertThat(reportRow(dismissed)).isEqualTo(dismissedBefore);
        assertThat(reportStatus(otherPollReport)).isEqualTo("PENDING");
        assertThat(pollStatus(otherPoll)).isEqualTo("PUBLIC");
    }

    @Test
    void 신고가_없는_공개_투표도_숨길_수_있다() {
        var poll = savePoll(newPoll(saveMember()));
        var admin = saveMember(Role.ADMIN);

        var result = act("hide", new Scene(poll.getAuthorId(), poll, null), admin, body(REASON));

        assertThat(result).hasStatus(204);
        assertThat(pollStatus(poll)).isEqualTo("HIDDEN");
        assertThat(actionCount()).isEqualTo(1L);
    }

    @Test
    void 이미_숨겨진_투표를_숨기면_409_POLL_NOT_PUBLIC이고_신고와_이력이_변하지_않는다() {
        var poll = savePoll(newHiddenPoll(saveMember()));
        var report = saveReport(poll);

        var result = act("hide", new Scene(poll.getAuthorId(), poll, report), saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_PUBLIC");
        assertThat(pollStatus(poll)).isEqualTo("HIDDEN");
        assertThat(reportStatus(report)).isEqualTo("PENDING");
        assertThat(actionCount()).isZero();
    }

    @Test
    void 삭제된_투표를_숨기면_404_POLL_NOT_FOUND이고_신고와_이력이_변하지_않는다() {
        var poll = savePoll(newDeletedPoll(saveMember()));
        var report = saveReport(poll);

        var result = act("hide", new Scene(poll.getAuthorId(), poll, report), saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
        assertThat(pollStatus(poll)).isEqualTo("DELETED");
        assertThat(reportStatus(report)).isEqualTo("PENDING");
        assertThat(actionCount()).isZero();
    }

    @Test
    void 같은_투표를_두_번_숨기면_두_번째는_409이고_이력은_첫_번째_것만_남는다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);
        act("hide", scene, admin, body(REASON));

        var result = act("hide", scene, admin, body(REASON));

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_PUBLIC");
        assertThat(actionCount()).isEqualTo(1L);
    }

    @Test
    void 투표_조치에_상태가_없으면_400_VALIDATION_FAILED다() {
        var scene = scene();

        var result = post(uri("hide", scene), saveMember(Role.ADMIN), body(REASON));

        assertValidationFailed(result);
        assertNothingChanged(scene);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLIC", "UNKNOWN"})
    void 투표_조치에_허용되지_않은_상태면_400_MALFORMED_REQUEST다(String status) {
        var scene = scene();

        var result = post(uri("hide", scene), saveMember(Role.ADMIN), "{\"status\": \"" + status + "\", \"reason\": \"" + REASON + "\"}");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
        assertNothingChanged(scene);
    }

    // ---- 투표 삭제 (API-명세 PATCH status(DELETED), 도메인-모델 Poll.deleteByAdmin) ----

    @Test
    void 삭제하면_투표가_DELETED이고_그_투표의_PENDING_신고가_모두_RESOLVED로_종료된다() {
        var scene = scene();
        var other = saveReport(scene.poll());
        var admin = saveMember(Role.ADMIN);

        var result = act("delete", scene, admin, body(REASON));

        assertThat(result).hasStatus(204);
        assertThat(pollStatus(scene.poll())).isEqualTo("DELETED");
        assertThat(reportStatus(scene.report())).isEqualTo("RESOLVED");
        assertThat(reportStatus(other)).isEqualTo("RESOLVED");
        assertThat(reportHandledBy(scene.report())).isEqualTo(admin);
        assertThat(reportHandledBy(other)).isEqualTo(admin);
        assertThat(memberStatus(scene.authorId())).isEqualTo("ACTIVE");
    }

    @Test
    void 숨겨진_투표도_삭제할_수_있고_신고를_RESOLVED로_종료한다() {
        var poll = savePoll(newHiddenPoll(saveMember()));
        var report = saveReport(poll);

        var result = act("delete", new Scene(poll.getAuthorId(), poll, report), saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(204);
        assertThat(pollStatus(poll)).isEqualTo("DELETED");
        assertThat(reportStatus(report)).isEqualTo("RESOLVED");
    }

    @Test
    void 삭제해도_이미_처리된_신고와_다른_투표의_신고는_그대로다() {
        var scene = scene();
        var dismissed = saveHandledReport(scene.poll(), ReportStatus.DISMISSED, saveMember(Role.ADMIN));
        var dismissedBefore = reportRow(dismissed);
        var otherPoll = savePoll(newPoll(saveMember()));
        var otherPollReport = saveReport(otherPoll);

        act("delete", scene, saveMember(Role.ADMIN), body(REASON));

        assertThat(reportRow(dismissed)).isEqualTo(dismissedBefore);
        assertThat(reportStatus(otherPollReport)).isEqualTo("PENDING");
        assertThat(pollStatus(otherPoll)).isEqualTo("PUBLIC");
    }

    @Test
    void 이미_삭제된_투표를_삭제하면_404_POLL_NOT_FOUND이고_신고와_이력이_변하지_않는다() {
        var poll = savePoll(newDeletedPoll(saveMember()));
        var report = saveReport(poll);

        var result = act("delete", new Scene(poll.getAuthorId(), poll, report), saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
        assertThat(reportStatus(report)).isEqualTo("PENDING");
        assertThat(actionCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hide", "delete"})
    void 삭제된_투표는_사유가_틀려도_400이_아니라_404_POLL_NOT_FOUND다(String action) {
        var poll = savePoll(newDeletedPoll(saveMember()));
        var scene = new Scene(poll.getAuthorId(), poll, saveReport(poll));
        var admin = saveMember(Role.ADMIN);

        assertThat(act(action, scene, admin, "{}")).hasStatus(404);
        assertThat(act(action, scene, admin, body("가".repeat(REASON_MAX + 1)))).hasStatus(404);
        assertThat(post(uri(action, scene), admin, "{\"reason\": \"" + REASON + "\"}")).hasStatus(404);
        assertThat(actionCount()).isZero();
    }

    // ---- 회원 정지 (API-명세 POST suspend, 도메인-모델 Member.suspend) ----

    @Test
    void 정지하면_회원이_SUSPENDED이고_정지_일시를_기록하며_신고와_기존_투표는_그대로다() {
        var scene = scene();
        var otherPollOfAuthor = savePoll(newPoll(scene.authorId()));

        var result = act("suspend", scene, saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(204);
        assertThat(memberStatus(scene.authorId())).isEqualTo("SUSPENDED");
        assertThat(jdbcTemplate.queryForObject("SELECT suspended_at FROM member WHERE id = ?", Object.class,
            scene.authorId())).isNotNull();
        assertThat(reportStatus(scene.report())).isEqualTo("PENDING");
        assertThat(pollStatus(scene.poll())).isEqualTo("PUBLIC");
        assertThat(pollStatus(otherPollOfAuthor)).isEqualTo("PUBLIC");
    }

    @Test
    void 신고와_상관없는_회원도_정지할_수_있다() {
        var member = saveMember();

        var result = post(API + "members/" + member + "/suspend", saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(204);
        assertThat(memberStatus(member)).isEqualTo("SUSPENDED");
    }

    @Test
    void 이미_정지된_회원을_정지하면_409_MEMBER_NOT_ACTIVE이고_이력이_남지_않는다() {
        var member = saveSuspendedMember();

        var result = post(API + "members/" + member + "/suspend", saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_ACTIVE");
        assertThat(memberStatus(member)).isEqualTo("SUSPENDED");
        assertThat(actionCount()).isZero();
    }

    @Test
    void 탈퇴한_회원을_정지하면_409_MEMBER_NOT_ACTIVE이고_상태와_이력이_변하지_않는다() {
        var member = saveWithdrawnMember();

        var result = post(API + "members/" + member + "/suspend", saveMember(Role.ADMIN), body(REASON));

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_ACTIVE");
        assertThat(memberStatus(member)).isEqualTo("WITHDRAWN");
        assertThat(actionCount()).isZero();
    }

    // ---- 이력 (API-명세, 도메인-모델 AdminAction, ERD admin_action; reportId는 기각에만 채운다) ----

    @Test
    void 기각_이력은_처리자_종류_대상_투표_신고_사유를_담고_한_건이다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);

        act("dismiss", scene, admin, body("문제없는 투표"));

        var rows = actionRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("admin_id")).isEqualTo(admin);
        assertThat(rows.getFirst().get("action_type")).isEqualTo("DISMISS_REPORT");
        assertThat(rows.getFirst().get("target_poll_id")).isEqualTo(scene.poll().getId());
        assertThat(rows.getFirst().get("target_member_id")).isNull();
        assertThat(rows.getFirst().get("report_id")).isEqualTo(scene.report().getId());
        assertThat(rows.getFirst().get("reason")).isEqualTo("문제없는 투표");
        assertThat(rows.getFirst().get("created_at")).isNotNull();
    }

    @Test
    void 숨김_이력은_대상_투표만_담고_신고와_회원은_비운다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);

        act("hide", scene, admin, body(REASON));

        var rows = actionRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("admin_id")).isEqualTo(admin);
        assertThat(rows.getFirst().get("action_type")).isEqualTo("HIDE_POLL");
        assertThat(rows.getFirst().get("target_poll_id")).isEqualTo(scene.poll().getId());
        assertThat(rows.getFirst().get("target_member_id")).isNull();
        assertThat(rows.getFirst().get("report_id")).isNull();
        assertThat(rows.getFirst().get("reason")).isEqualTo(REASON);
    }

    @Test
    void 삭제_이력은_대상_투표만_담고_신고와_회원은_비운다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);

        act("delete", scene, admin, body(REASON));

        var rows = actionRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("admin_id")).isEqualTo(admin);
        assertThat(rows.getFirst().get("action_type")).isEqualTo("DELETE_POLL");
        assertThat(rows.getFirst().get("target_poll_id")).isEqualTo(scene.poll().getId());
        assertThat(rows.getFirst().get("target_member_id")).isNull();
        assertThat(rows.getFirst().get("report_id")).isNull();
        assertThat(rows.getFirst().get("reason")).isEqualTo(REASON);
    }

    @Test
    void 정지_이력은_대상_회원만_담고_투표와_신고는_비운다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);

        act("suspend", scene, admin, body(REASON));

        var rows = actionRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("admin_id")).isEqualTo(admin);
        assertThat(rows.getFirst().get("action_type")).isEqualTo("SUSPEND_MEMBER");
        assertThat(rows.getFirst().get("target_member_id")).isEqualTo(scene.authorId());
        assertThat(rows.getFirst().get("target_poll_id")).isNull();
        assertThat(rows.getFirst().get("report_id")).isNull();
        assertThat(rows.getFirst().get("reason")).isEqualTo(REASON);
    }

    @Test
    void 같이_종료된_신고가_여럿이어도_숨김_이력은_한_건이다() {
        var scene = scene();
        saveReport(scene.poll());
        saveReport(scene.poll());

        act("hide", scene, saveMember(Role.ADMIN), body(REASON));

        assertThat(actionCount()).isEqualTo(1L);
    }

    @Test
    void 같이_종료된_신고가_여럿이어도_기각_이력은_한_건이다() {
        var scene = scene();
        saveReport(scene.poll());
        saveReport(scene.poll());

        act("dismiss", scene, saveMember(Role.ADMIN), body(REASON));

        assertThat(actionCount()).isEqualTo(1L);
    }

    // ---- 상세의 actions (API-명세 GET 상세: 이 신고의 기각 이력 + 대상 투표의 숨김·삭제 이력, 시각 순) ----

    @Test
    void 기각한_뒤_상세의_actions는_기각_이력을_담고_신고_상태와_처리자도_채워진다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);
        act("dismiss", scene, admin, body("문제없는 투표"));

        var result = detail(scene.report(), admin);

        assertThat(result).hasStatus(200);
        var json = bodyOf(result);
        assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("DISMISSED");
        assertThat(((Number) JsonPath.read(json, "$.handledBy")).longValue()).isEqualTo(admin);
        assertThat((List<?>) JsonPath.read(json, "$.actions")).hasSize(1);
        assertThat((String) JsonPath.read(json, "$.actions[0].actionType")).isEqualTo("DISMISS_REPORT");
        assertThat(((Number) JsonPath.read(json, "$.actions[0].adminId")).longValue()).isEqualTo(admin);
        assertThat((String) JsonPath.read(json, "$.actions[0].reason")).isEqualTo("문제없는 투표");
        assertThat((String) JsonPath.read(json, "$.actions[0].createdAt")).isNotBlank();
    }

    @Test
    void 숨긴_뒤_상세의_actions는_대상_투표의_숨김_이력을_담는다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);
        act("hide", scene, admin, body(REASON));

        var json = bodyOf(detail(scene.report(), admin));

        assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("RESOLVED");
        assertThat((List<?>) JsonPath.read(json, "$.actions")).hasSize(1);
        assertThat((String) JsonPath.read(json, "$.actions[0].actionType")).isEqualTo("HIDE_POLL");
        assertThat((String) JsonPath.read(json, "$.actions[0].reason")).isEqualTo(REASON);
    }

    @Test
    void 함께_종료된_다른_신고의_상세_actions도_대상_투표의_숨김_이력을_담는다() {
        var scene = scene();
        var other = saveReport(scene.poll());
        var admin = saveMember(Role.ADMIN);
        act("hide", scene, admin, body(REASON));

        var json = bodyOf(detail(other, admin));

        assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("RESOLVED");
        assertThat((List<?>) JsonPath.read(json, "$.actions")).hasSize(1);
        assertThat((String) JsonPath.read(json, "$.actions[0].actionType")).isEqualTo("HIDE_POLL");
    }

    @Test
    void 기각_뒤_숨기면_상세의_actions는_기각_이력과_숨김_이력을_id_오름차순으로_담는다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);
        act("dismiss", scene, admin, body("문제없음"));
        act("hide", scene, admin, body("다시 보니 문제"));

        var json = bodyOf(detail(scene.report(), admin));

        assertThat((List<?>) JsonPath.read(json, "$.actions")).hasSize(2);
        assertThat((String) JsonPath.read(json, "$.actions[0].actionType")).isEqualTo("DISMISS_REPORT");
        assertThat((String) JsonPath.read(json, "$.actions[1].actionType")).isEqualTo("HIDE_POLL");
        var firstId = ((Number) JsonPath.read(json, "$.actions[0].id")).longValue();
        var secondId = ((Number) JsonPath.read(json, "$.actions[1].id")).longValue();
        assertThat(firstId).isLessThan(secondId);
    }

    @Test
    void 기각_이력은_같이_기각된_다른_신고의_상세에는_나오지_않는다() {
        var scene = scene();
        var other = saveReport(scene.poll());
        var admin = saveMember(Role.ADMIN);
        act("dismiss", scene, admin, body(REASON));

        var result = detail(other, admin);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("DISMISSED");
        assertThat(result).bodyJson().extractingPath("$.actions").asList().isEmpty();
    }

    @Test
    void 숨긴_뒤_삭제하면_상세의_actions는_숨김_이력_삭제_이력_순이다() {
        var scene = scene();
        var admin = saveMember(Role.ADMIN);
        act("hide", scene, admin, body(REASON));
        act("delete", scene, admin, body(REASON));

        var json = bodyOf(detail(scene.report(), admin));

        assertThat((List<?>) JsonPath.read(json, "$.actions")).hasSize(2);
        assertThat((String) JsonPath.read(json, "$.actions[0].actionType")).isEqualTo("HIDE_POLL");
        assertThat((String) JsonPath.read(json, "$.actions[1].actionType")).isEqualTo("DELETE_POLL");
    }

    @Test
    void 회원_정지_이력과_다른_투표의_이력은_상세의_actions에_나오지_않는다() {
        var scene = scene();
        var otherPoll = savePoll(newPoll(saveMember()));
        var otherPollReport = saveReport(otherPoll);
        var admin = saveMember(Role.ADMIN);
        act("suspend", scene, admin, body(REASON));
        act("hide", new Scene(otherPoll.getAuthorId(), otherPoll, otherPollReport), admin, body(REASON));

        var result = detail(scene.report(), admin);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        assertThat(result).bodyJson().extractingPath("$.actions").asList().isEmpty();
    }

    // ---- 헬퍼 ----

    private Scene scene() {
        var author = saveMember();
        var poll = savePoll(newPoll(author));

        return new Scene(author, poll, saveReport(poll));
    }

    private String uri(String action, Scene scene) {
        return switch (action) {
            case "dismiss" -> API + "reports/" + scene.report().getId() + "/dismiss";
            case "hide", "delete" -> API + "polls/" + scene.poll().getPublicId().value() + "/status";
            case "suspend" -> API + "members/" + scene.authorId() + "/suspend";
            default -> throw new IllegalArgumentException(action);
        };
    }

    private String missingUri(String action) {
        return switch (action) {
            case "dismiss" -> API + "reports/999999/dismiss";
            case "hide", "delete" -> API + "polls/" + MISSING_POLL_ID + "/status";
            case "suspend" -> API + "members/999999/suspend";
            default -> throw new IllegalArgumentException(action);
        };
    }

    /** 숨김·삭제는 같은 API라 요청 본문에 조치 상태(HIDDEN·DELETED)를 붙인다. */
    private MvcTestResult act(String action, Scene scene, Long adminId, String content) {
        return post(uri(action, scene), adminId, withStatus(action, content));
    }

    private String withStatus(String action, String content) {
        return switch (action) {
            case "hide" -> content.replaceFirst("\\{\\s*", "{\"status\": \"HIDDEN\"" + (content.equals("{}") ? "" : ", "));
            case "delete" -> content.replaceFirst("\\{\\s*", "{\"status\": \"DELETED\"" + (content.equals("{}") ? "" : ", "));
            default -> content;
        };
    }

    private MvcTestResult post(String uri, Long memberId, String content) {
        // 테스트는 한 트랜잭션이라 영속성 컨텍스트가 요청 사이에 남는다. 실제 요청처럼 매번 DB에서 새로 읽도록 비운다.
        entityManager.flush();
        entityManager.clear();

        var result = request(uri)
            .header("Origin", ALLOWED_ORIGIN)
            .cookie(accessCookie(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(content)
            .exchange();

        // 실제 요청은 커밋 때 변경이 반영된다. 테스트는 커밋하지 않으므로, JdbcTemplate으로 읽기 전에 직접 반영한다.
        entityManager.flush();

        return result;
    }

    /** 투표 숨김·삭제(/status)는 PATCH, 나머지는 POST다. */
    private MockMvcTester.MockMvcRequestBuilder request(String uri) {
        return (uri.endsWith("/status") ? mvcTester.patch() : mvcTester.post()).uri(uri);
    }

    private MvcTestResult detail(Report report, Long adminId) {
        entityManager.flush();
        entityManager.clear();

        return mvcTester.get().uri(API + "reports/" + report.getId()).cookie(accessCookie(adminId)).exchange();
    }

    /** reason은 JSON 이스케이프(\t, \n)를 이미 포함한 문자열로 받는다. */
    private String body(String reason) {
        return "{\"reason\": \"" + reason + "\"}";
    }

    private void assertForbidden(MvcTestResult result) {
        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    private void assertValidationFailed(MvcTestResult result) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    private void assertNothingChanged(Scene scene) {
        assertThat(reportStatus(scene.report())).isEqualTo("PENDING");
        assertThat(pollStatus(scene.poll())).isEqualTo("PUBLIC");
        assertThat(memberStatus(scene.authorId())).isEqualTo("ACTIVE");
        assertThat(actionCount()).isZero();
    }

    private Report saveReport(Poll poll) {
        return reportJpaRepository.saveAndFlush(newReport(poll.getId()));
    }

    private Report saveHandledReport(Poll poll, ReportStatus status, Long adminId) {
        return reportJpaRepository.saveAndFlush(handled(newReport(poll.getId()), status, adminId));
    }

    private Long saveWithdrawnMember() {
        var member = MemberFixture.changeStatus(MemberFixture.createMember(), MemberStatus.WITHDRAWN);

        return memberJpaRepository.saveAndFlush(member).getId();
    }

    private String reportStatus(Report report) {
        return jdbcTemplate.queryForObject("SELECT status FROM report WHERE id = ?", String.class, report.getId());
    }

    private Long reportHandledBy(Report report) {
        return jdbcTemplate.queryForObject("SELECT handled_by FROM report WHERE id = ?", Long.class, report.getId());
    }

    private Map<String, Object> reportRow(Report report) {
        return jdbcTemplate.queryForMap("SELECT status, handled_at, handled_by FROM report WHERE id = ?",
            report.getId());
    }

    private String pollStatus(Poll poll) {
        return jdbcTemplate.queryForObject("SELECT status FROM poll WHERE id = ?", String.class, poll.getId());
    }

    private String memberStatus(Long memberId) {
        return jdbcTemplate.queryForObject("SELECT status FROM member WHERE id = ?", String.class, memberId);
    }

    private long actionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_action", Long.class);
    }

    private List<Map<String, Object>> actionRows() {
        return jdbcTemplate.queryForList(
            "SELECT admin_id, action_type, target_poll_id, target_member_id, report_id, reason, created_at "
                + "FROM admin_action ORDER BY id");
    }

    private String bodyOf(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    /** 토큰의 role은 DB 판정에 쓰이지 않으므로 실제 DB 역할과 같게 만든다. */
    private Cookie accessCookie(Long memberId) {
        var role = memberJpaRepository.findById(memberId).orElseThrow().getRole();

        return new Cookie(AuthCookies.ACCESS_COOKIE,
            jwtTokenProvider.createAccessToken(memberId, role, LocalDateTime.now(TimeConfig.KST)));
    }

}
