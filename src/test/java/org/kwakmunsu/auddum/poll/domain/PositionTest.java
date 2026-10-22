package org.kwakmunsu.auddum.poll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.support.error.AuddumException;

class PositionTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void 순번_1부터_5까지는_만들_수_있다(int value) {
        assertThat(Position.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    void 순번이_1_5_범위_밖이면_만들_수_없다(int value) {
        assertThatThrownBy(() -> Position.of(value))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지 순서는 1~5여야 합니다.");
    }

}
