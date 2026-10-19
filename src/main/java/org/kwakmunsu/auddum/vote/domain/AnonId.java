package org.kwakmunsu.auddum.vote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.kwakmunsu.auddum.support.error.AuddumException;

/**
 * 비회원 구분용 익명 식별자(UUID). 서명 검증을 통과한 쿠키에서 꺼내거나 서버가 새로 발급한 값만 만든다.
 */
@Embeddable
public record AnonId(
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "anon_id", length = 36)
    UUID value
) {

    public AnonId {
        if (value == null) {
            throw AuddumException.validation("anonId", "익명 식별자는 필수입니다.");
        }
    }

}
