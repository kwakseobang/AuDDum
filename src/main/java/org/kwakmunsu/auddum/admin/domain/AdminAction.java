package org.kwakmunsu.auddum.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.jpa.BaseEntity;

/**
 * 관리자 조치 애그리거트 루트. 관리자가 한 조치의 추가 전용 이력이라 만든 뒤 바꾸거나 지우지 않는다.
 * 모든 조치에 처리자, 조치 종류, 사유를 남기고 시각은 {@code createdAt}이 채운다. 대상은 ID 값으로만 가리킨다.
 */
@Table(name = "admin_action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class AdminAction extends BaseEntity {

    /** 사유 글자 수 상한(유니코드 문자 단위). reason 컬럼 길이와 맞춘다. */
    public static final int REASON_MAX_LENGTH = 200;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false, length = 30)
    private ActionType type;

    @Column(name = "target_poll_id", updatable = false)
    private Long targetPollId;

    @Column(name = "target_member_id", updatable = false)
    private Long targetMemberId;

    @Column(name = "report_id", updatable = false)
    private Long reportId;

    @Column(name = "reason", updatable = false, length = REASON_MAX_LENGTH)
    private String reason;

    /** 조치 이력을 기록한다. 사유가 비어 있거나 {@value #REASON_MAX_LENGTH}자를 넘으면 400 VALIDATION_FAILED다. */
    public static AdminAction record(
        @NonNull Long adminId,
        @NonNull ActionType type,
        Long targetPollId,
        Long targetMemberId,
        Long reportId,
        String reason
    ) {
        validateReason(reason);

        AdminAction action = new AdminAction();

        action.adminId = adminId;
        action.type = type;
        action.targetPollId = targetPollId;
        action.targetMemberId = targetMemberId;
        action.reportId = reportId;
        action.reason = reason;

        return action;
    }

    /** 신고 기각 이력. 신고와 대상 투표를 남긴다. */
    public static AdminAction dismissReport(Long adminId, Long pollId, Long reportId, String reason) {
        return record(adminId, ActionType.DISMISS_REPORT, pollId, null, reportId, reason);
    }

    /** 투표 숨김·삭제 이력. 조치 종류는 {@link PollTakedown}이 정한다. 상태가 없으면 400 VALIDATION_FAILED다. */
    public static AdminAction takedownPoll(Long adminId, Long pollId, PollTakedown takedown, String reason) {
        if (takedown == null) {
            throw AuddumException.validation("status", "상태는 필수입니다.");
        }

        return record(adminId, takedown.actionType(), pollId, null, null, reason);
    }

    public static AdminAction suspendMember(Long adminId, Long memberId, String reason) {
        return record(adminId, ActionType.SUSPEND_MEMBER, null, memberId, null, reason);
    }

    /** 사유가 비어 있거나 상한을 넘으면 400 VALIDATION_FAILED다. */
    private static void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw AuddumException.validation("reason", "사유는 필수입니다.");
        }
        if (reason.codePointCount(0, reason.length()) > REASON_MAX_LENGTH) {
            throw AuddumException.validation("reason", "사유는 " + REASON_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

}
