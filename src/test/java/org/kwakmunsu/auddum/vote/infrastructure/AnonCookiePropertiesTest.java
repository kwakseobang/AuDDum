package org.kwakmunsu.auddum.vote.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnonCookiePropertiesTest {

    @Test
    void 서명_키가_없으면_만들_수_없다() {
        assertThatThrownBy(() -> new AnonCookieProperties(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 서명_키가_31바이트면_만들_수_없다() {
        assertThatThrownBy(() -> new AnonCookieProperties("a".repeat(31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 서명_키가_정확히_32바이트면_만들_수_있다() {
        assertThatCode(() -> new AnonCookieProperties("a".repeat(32)))
                .doesNotThrowAnyException();
    }

    @Test
    void 서명_키는_글자_수가_아니라_바이트_수로_센다() {
        // 한글 11자 = UTF-8 33바이트, 글자 수는 32 미만
        assertThatCode(() -> new AnonCookieProperties("가".repeat(11)))
                .doesNotThrowAnyException();
    }

}
