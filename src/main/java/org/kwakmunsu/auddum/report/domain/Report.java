package org.kwakmunsu.auddum.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.jpa.BaseEntity;

/**
 * 신고 애그리거트 루트. 부적절한 투표에 대한 접수와 처리 상태를 가진다. 투표와는 ID 값으로만 연결한다.
 * 같은 신고자의 같은 투표 중복 신고는 DB 유니크 제약이 마지막으로 막는다.
 */
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Report extends BaseEntity {

    @Column(name = "poll_id", nullable = false, updatable = false)
    private Long pollId;

    @Embedded
    private Reporter reporter;

    @Embedded
    private ReportReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    /** 동시에 들어온 처리 요청(더블클릭·재전송)에서 한쪽만 반영되도록 하는 낙관적 락 버전. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "handled_by")
    private Long handledBy;

    /**
     * 신고를 접수한다. 상태는 PENDING이고 투표의 노출 상태는 바꾸지 않는다.
     * 숨김·삭제된 투표인지, 정지 회원인지, 이미 신고했는지는 접수 전에 서비스가 확인한다.
     * 접수 시각은 {@code createdAt}이 자동으로 채우고, 처리 시각({@code handledAt})은 처리할 때 정한다.
     */
    public static Report submit(
        @NonNull Long pollId,
        @NonNull Reporter reporter,
        @NonNull ReportReason reason
    ) {
        Report report = new Report();

        report.pollId = pollId;
        report.reporter = reporter;
        report.reason = reason;
        report.status = ReportStatus.PENDING;

        return report;
    }

    /** 기각한다. 대기(PENDING) 신고만 DISMISSED로 바꾸고 처리 일시·처리자를 남긴다. 이미 처리된 신고는 409다. */
    public void dismiss(@NonNull Long adminId, @NonNull LocalDateTime now) {
        close(ReportStatus.DISMISSED, adminId, now);
    }

    private void close(ReportStatus result, Long adminId, LocalDateTime now) {
        if (!isPending()) {
            throw new AuddumException(ReportErrorCode.REPORT_ALREADY_HANDLED);
        }

        this.status = result;
        this.handledAt = now;
        this.handledBy = adminId;
    }

    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }

}
