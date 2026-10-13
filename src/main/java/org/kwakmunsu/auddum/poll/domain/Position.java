package org.kwakmunsu.auddum.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.kwakmunsu.auddum.support.error.AuddumException;

/**
 * 선택지의 표시 순서(1~5). 외부에는 선택지 id 대신 이 값으로 가리킨다.
 */
@Embeddable
public record Position(
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "position", nullable = false)
    int value
) {

    static final int MIN = 1;
    static final int MAX = 5;

    public Position {
        if (value < MIN || value > MAX) {
            throw AuddumException.validation("position", "선택지 순서는 1~5여야 합니다.");
        }
    }

    public static Position of(int value) {
        return new Position(value);
    }

}
