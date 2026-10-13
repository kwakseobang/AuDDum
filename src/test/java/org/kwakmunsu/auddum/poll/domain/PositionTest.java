package org.kwakmunsu.auddum.poll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.AuddumException;

class PositionTest {

    @Test
    void 순번_1은_만들_수_있다() {
        assertThat(Position.of(1).value()).isEqualTo(1);
    }

    @Test
    void 순번_5는_만들_수_있다() {
        assertThat(Position.of(5).value()).isEqualTo(5);
    }

    @Test
    void 순번_0은_만들_수_없다() {
        assertThatThrownBy(() -> Position.of(0))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지 순서는 1~5여야 합니다.");
    }

    @Test
    void 순번_6은_만들_수_없다() {
        assertThatThrownBy(() -> Position.of(6))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지 순서는 1~5여야 합니다.");
    }

    @Test
    void 음수_순번은_만들_수_없다() {
        assertThatThrownBy(() -> Position.of(-1))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지 순서는 1~5여야 합니다.");
    }

}
