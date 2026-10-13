package org.kwakmunsu.auddum.poll.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.kwakmunsu.auddum.support.jpa.BaseEntity;

/** 선택지. {@link Poll}을 통해서만 만들고, 외부에는 id 대신 position으로 가리킨다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "poll_option")
@Entity
public class PollOption extends BaseEntity {

    @Embedded
    private Position position;

    @Embedded
    private OptionContent content;

    PollOption(Position position, OptionContent content) {
        this.position = position;
        this.content = content;
    }

    boolean isAt(int position) {
        return this.position.value() == position;
    }

}
