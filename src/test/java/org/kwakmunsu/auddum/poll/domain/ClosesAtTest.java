package org.kwakmunsu.auddum.poll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.AuddumException;

class ClosesAtTest {

    private static final java.time.LocalDateTime NOW = PollFixture.NOW;

    @Test
    void 미래_마감_시각은_만들_수_있다() {
        assertThat(ClosesAt.of(NOW.plusNanos(1), NOW).value()).isEqualTo(NOW.plusNanos(1));
    }

    @Test
    void 아주_먼_미래도_상한이_없어_만들_수_있다() {
        assertThat(ClosesAt.of(NOW.plusYears(100), NOW).value()).isEqualTo(NOW.plusYears(100));
    }

    @Test
    void 현재와_같은_마감_시각은_만들_수_없다() {
        assertThatThrownBy(() -> ClosesAt.of(NOW, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("마감 시각은 현재보다 뒤여야 합니다.");
    }

    @Test
    void 과거_마감_시각은_만들_수_없다() {
        assertThatThrownBy(() -> ClosesAt.of(NOW.minusNanos(1), NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("마감 시각은 현재보다 뒤여야 합니다.");
    }

    @Test
    void 마감_시각_직전에는_마감되지_않았다() {
        var closesAt = ClosesAt.of(NOW.plusHours(1), NOW);

        assertThat(closesAt.isReachedAt(NOW.plusHours(1).minusNanos(1))).isFalse();
    }

    @Test
    void 마감_시각과_정확히_같으면_마감이다() {
        var closesAt = ClosesAt.of(NOW.plusHours(1), NOW);

        assertThat(closesAt.isReachedAt(NOW.plusHours(1))).isTrue();
    }

    @Test
    void 마감_시각_직후에는_마감이다() {
        var closesAt = ClosesAt.of(NOW.plusHours(1), NOW);

        assertThat(closesAt.isReachedAt(NOW.plusHours(1).plusNanos(1))).isTrue();
    }

}
