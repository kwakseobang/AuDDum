package org.kwakmunsu.auddum.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.kwakmunsu.auddum.support.error.AuddumException;

/**
 * 신고를 한 주체. 회원 ID와 익명 식별자(UUID) 중 정확히 하나다. 같은 신고자인지는 값 동등성으로 판단한다.
 * 참여자({@code Voter})와 모양이 같아도 신고 애그리거트의 별개 값 객체이므로 공유하지 않는다.
 */
@Embeddable
public record Reporter(
    @Column(name = "reporter_member_id")
    Long memberId,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reporter_anon_id", length = 36)
    UUID anonId
) {

    public Reporter {
        if ((memberId == null) == (anonId == null)) {
            throw AuddumException.validation("reporter", "신고자는 회원 ID와 익명 식별자 중 하나여야 합니다.");
        }
    }

    public static Reporter member(Long memberId) {
        return new Reporter(memberId, null);
    }

    public static Reporter anonymous(UUID anonId) {
        return new Reporter(null, anonId);
    }

}
