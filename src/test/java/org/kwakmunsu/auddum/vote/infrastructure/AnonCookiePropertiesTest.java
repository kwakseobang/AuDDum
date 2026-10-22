package org.kwakmunsu.auddum.vote.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AnonCookiePropertiesTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void 서명_키가_없거나_32바이트_미만이면_만들_수_없다(String secret) {
        // "" 는 빈 키, 마지막 값은 31바이트
        assertThatThrownBy(() -> new AnonCookieProperties(secret))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",   // 정확히 32바이트
        "가가가가가가가가가가가"                  // 한글 11자 = UTF-8 33바이트, 글자 수는 32 미만이지만 바이트 수로 센다
    })
    void 서명_키가_32바이트_이상이면_글자_수가_아니라_바이트_수로_세어_만들_수_있다(String secret) {
        assertThatCode(() -> new AnonCookieProperties(secret)).doesNotThrowAnyException();
    }

}
