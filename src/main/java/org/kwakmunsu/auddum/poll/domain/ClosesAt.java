package org.kwakmunsu.auddum.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import org.kwakmunsu.auddum.support.error.AuddumException;

/**
 * 마감 시각(KST). 생성 시점에 미래여야 하고 상한은 없다. 마감 여부는 저장하지 않고 계산한다. 미래 검증은 생성 시점(now)이 필요해 {@link #of}에서 하고, DB에서 읽을 때는 과거 시각도 그대로 읽는다.
 */
@Embeddable
public record ClosesAt(
    @Column(name = "close_at")
    LocalDateTime value
) {

    public ClosesAt {
        if (value == null) {
            throw AuddumException.validation("closesAt", "마감 시각이 필요합니다.");
        }
    }

    /**
     * now보다 뒤인 시각만 허용한다. now와 같거나 과거면 거부한다.
     */
    public static ClosesAt of(LocalDateTime value, LocalDateTime now) {
        if (value == null || !value.isAfter(now)) {
            throw AuddumException.validation("closesAt", "마감 시각은 현재보다 뒤여야 합니다.");
        }

        return new ClosesAt(value);
    }

    /**
     * now가 마감 시각 이상이면 마감이다.
     */
    public boolean isReachedAt(LocalDateTime now) {
        return !now.isBefore(value);
    }

}
