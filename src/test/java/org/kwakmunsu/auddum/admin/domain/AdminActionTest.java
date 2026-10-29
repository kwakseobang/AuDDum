package org.kwakmunsu.auddum.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.support.error.AuddumException;

/**
 * 관리자 조치 이력. 기준은 도메인-모델 §7 AdminAction 행위 표 record(...)
 * ("사유가 비어 있거나 200자를 넘으면 거절한다"), 규칙("사유는 필수다"), ActionType 표, ERD admin_action(reason VARCHAR(200)).
 */
class AdminActionTest {

    private static final int REASON_MAX = 200;

    @Test
    void 조치_종류는_도메인_모델_표의_여섯_가지다() {
        assertThat(ActionType.values()).extracting(Enum::name).containsExactlyInAnyOrder(
            "HIDE_POLL", "DELETE_POLL", "RESTORE_POLL", "SUSPEND_MEMBER", "UNSUSPEND_MEMBER", "DISMISS_REPORT");
    }

    @Test
    void 투표_조치를_기록하면_처리자_종류_대상_투표_신고_사유를_그대로_가진다() {
        var action = AdminAction.record(1L, ActionType.HIDE_POLL, 10L, null, 20L, "특정인 비방 내용 확인");

        assertThat(action.getAdminId()).isEqualTo(1L);
        assertThat(action.getType()).isEqualTo(ActionType.HIDE_POLL);
        assertThat(action.getTargetPollId()).isEqualTo(10L);
        assertThat(action.getReportId()).isEqualTo(20L);
        assertThat(action.getReason()).isEqualTo("특정인 비방 내용 확인");
    }

    @Test
    void 회원_조치를_기록하면_대상_회원을_가진다() {
        var action = AdminAction.record(1L, ActionType.SUSPEND_MEMBER, null, 5L, 20L, "반복 위반");

        assertThat(action.getType()).isEqualTo(ActionType.SUSPEND_MEMBER);
        assertThat(action.getTargetMemberId()).isEqualTo(5L);
        assertThat(action.getReportId()).isEqualTo(20L);
    }

    @Test
    void 신고_없이_하는_조치도_기록할_수_있다() {
        assertThatCode(() -> AdminAction.record(1L, ActionType.RESTORE_POLL, 10L, null, null, "오인 숨김"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n", " \t\n "})
    void 사유가_없거나_공백뿐이면_기록할_수_없다(String reason) {
        assertThatThrownBy(() -> AdminAction.record(1L, ActionType.HIDE_POLL, 10L, null, 20L, reason))
            .isInstanceOf(AuddumException.class);
    }

    @Test
    void 사유가_한_글자면_기록할_수_있다() {
        assertThatCode(() -> AdminAction.record(1L, ActionType.HIDE_POLL, 10L, null, 20L, "가"))
            .doesNotThrowAnyException();
    }

    @Test
    void 사유가_상한인_200자면_기록할_수_있다() {
        assertThatCode(() -> AdminAction.record(1L, ActionType.HIDE_POLL, 10L, null, 20L, "가".repeat(REASON_MAX)))
            .doesNotThrowAnyException();
    }

    @Test
    void 사유가_상한을_넘어_201자면_기록할_수_없다() {
        assertThatThrownBy(() ->
            AdminAction.record(1L, ActionType.HIDE_POLL, 10L, null, 20L, "가".repeat(REASON_MAX + 1)))
            .isInstanceOf(AuddumException.class);
    }

    // ---- 기록 편의 팩토리 (도메인-모델 AdminAction dismissReport / takedownPoll / suspendMember; 설계-결정 "이력의 reportId는 기각에만 채운다"; dismissReport(adminId, pollId, reportId, reason), takedownPoll(adminId, pollId, takedown, reason), suspendMember(adminId, memberId, reason)) ----

    @Test
    void dismissReport는_신고_기각_종류와_대상_투표를_채우고_대상_회원은_비운다() {
        var action = AdminAction.dismissReport(1L, 10L, 20L, "문제 없음");

        assertThat(action.getAdminId()).isEqualTo(1L);
        assertThat(action.getType()).isEqualTo(ActionType.DISMISS_REPORT);
        assertThat(action.getTargetPollId()).isEqualTo(10L);
        assertThat(action.getTargetMemberId()).isNull();
        assertThat(action.getReportId()).isEqualTo(20L);
        assertThat(action.getReason()).isEqualTo("문제 없음");
    }

    @Test
    void takedownPoll은_숨김일_때_투표_숨김_종류와_대상_투표를_채우고_대상_회원은_비운다() {
        var action = AdminAction.takedownPoll(1L, 10L, PollTakedown.HIDDEN, "비방");

        assertThat(action.getAdminId()).isEqualTo(1L);
        assertThat(action.getType()).isEqualTo(ActionType.HIDE_POLL);
        assertThat(action.getTargetPollId()).isEqualTo(10L);
        assertThat(action.getTargetMemberId()).isNull();
        assertThat(action.getReportId()).isNull();
        assertThat(action.getReason()).isEqualTo("비방");
    }

    @Test
    void takedownPoll은_삭제일_때_투표_삭제_종류와_대상_투표를_채우고_대상_회원은_비운다() {
        var action = AdminAction.takedownPoll(1L, 10L, PollTakedown.DELETED, "불법 내용");

        assertThat(action.getAdminId()).isEqualTo(1L);
        assertThat(action.getType()).isEqualTo(ActionType.DELETE_POLL);
        assertThat(action.getTargetPollId()).isEqualTo(10L);
        assertThat(action.getTargetMemberId()).isNull();
        assertThat(action.getReportId()).isNull();
        assertThat(action.getReason()).isEqualTo("불법 내용");
    }

    @Test
    void suspendMember는_회원_정지_종류와_대상_회원을_채우고_대상_투표는_비운다() {
        var action = AdminAction.suspendMember(1L, 5L, "반복 위반");

        assertThat(action.getAdminId()).isEqualTo(1L);
        assertThat(action.getType()).isEqualTo(ActionType.SUSPEND_MEMBER);
        assertThat(action.getTargetMemberId()).isEqualTo(5L);
        assertThat(action.getTargetPollId()).isNull();
        assertThat(action.getReportId()).isNull();
        assertThat(action.getReason()).isEqualTo("반복 위반");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void 기록_편의_팩토리는_사유가_없거나_공백뿐이면_거절한다(String reason) {
        assertThatThrownBy(() -> AdminAction.dismissReport(1L, 10L, 20L, reason)).isInstanceOf(AuddumException.class);
        assertThatThrownBy(() -> AdminAction.takedownPoll(1L, 10L, PollTakedown.HIDDEN, reason)).isInstanceOf(AuddumException.class);
        assertThatThrownBy(() -> AdminAction.takedownPoll(1L, 10L, PollTakedown.DELETED, reason)).isInstanceOf(AuddumException.class);
        assertThatThrownBy(() -> AdminAction.suspendMember(1L, 5L, reason)).isInstanceOf(AuddumException.class);
    }

    @Test
    void 기록_편의_팩토리는_사유_200자까지_받고_201자는_거절한다() {
        var max = "가".repeat(REASON_MAX);
        var over = "가".repeat(REASON_MAX + 1);

        assertThatCode(() -> AdminAction.dismissReport(1L, 10L, 20L, max)).doesNotThrowAnyException();
        assertThatCode(() -> AdminAction.takedownPoll(1L, 10L, PollTakedown.HIDDEN, max)).doesNotThrowAnyException();
        assertThatCode(() -> AdminAction.takedownPoll(1L, 10L, PollTakedown.DELETED, max)).doesNotThrowAnyException();
        assertThatCode(() -> AdminAction.suspendMember(1L, 5L, max)).doesNotThrowAnyException();
        assertThatThrownBy(() -> AdminAction.dismissReport(1L, 10L, 20L, over)).isInstanceOf(AuddumException.class);
        assertThatThrownBy(() -> AdminAction.takedownPoll(1L, 10L, PollTakedown.HIDDEN, over)).isInstanceOf(AuddumException.class);
        assertThatThrownBy(() -> AdminAction.takedownPoll(1L, 10L, PollTakedown.DELETED, over)).isInstanceOf(AuddumException.class);
        assertThatThrownBy(() -> AdminAction.suspendMember(1L, 5L, over)).isInstanceOf(AuddumException.class);
    }

}
