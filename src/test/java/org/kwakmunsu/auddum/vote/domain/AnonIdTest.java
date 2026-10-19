package org.kwakmunsu.auddum.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.AuddumException;

class AnonIdTest {

    @Test
    void UUID로_익명_식별자를_만들_수_있다() {
        var uuid = UUID.randomUUID();

        assertThat(new AnonId(uuid).value()).isEqualTo(uuid);
    }

    @Test
    void UUID가_null이면_만들_수_없다() {
        assertThatThrownBy(() -> new AnonId(null))
                .isInstanceOf(AuddumException.class);
    }

}
