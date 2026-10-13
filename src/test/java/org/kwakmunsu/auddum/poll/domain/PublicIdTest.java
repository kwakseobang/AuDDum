package org.kwakmunsu.auddum.poll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.AuddumException;

class PublicIdTest {

    @Test
    void 열두_자_영숫자는_공개_ID가_된다() {
        assertThat(PublicId.of("aB3dE6gH9jK1").value()).isEqualTo("aB3dE6gH9jK1");
    }

    @Test
    void 열한_자는_공개_ID가_될_수_없다() {
        assertThatThrownBy(() -> PublicId.of("aB3dE6gH9jK"))
                .isInstanceOf(AuddumException.class)
                .hasMessage("공개 ID는 12자 영숫자여야 합니다.");
    }

    @Test
    void 열세_자는_공개_ID가_될_수_없다() {
        assertThatThrownBy(() -> PublicId.of("aB3dE6gH9jK12"))
                .isInstanceOf(AuddumException.class)
                .hasMessage("공개 ID는 12자 영숫자여야 합니다.");
    }

    @Test
    void 영숫자가_아닌_문자가_있으면_공개_ID가_될_수_없다() {
        assertThatThrownBy(() -> PublicId.of("aB3dE6gH9j-1"))
                .isInstanceOf(AuddumException.class)
                .hasMessage("공개 ID는 12자 영숫자여야 합니다.");
    }

    @Test
    void 한글이_섞인_열두_자는_공개_ID가_될_수_없다() {
        assertThatThrownBy(() -> PublicId.of("가나다라마바사아자차카타"))
                .isInstanceOf(AuddumException.class)
                .hasMessage("공개 ID는 12자 영숫자여야 합니다.");
    }

    @Test
    void 공백이_섞인_열두_자는_공개_ID가_될_수_없다() {
        assertThatThrownBy(() -> PublicId.of("aB3dE6gH9jK "))
                .isInstanceOf(AuddumException.class)
                .hasMessage("공개 ID는 12자 영숫자여야 합니다.");
    }

    @Test
    void 발급한_공개_ID는_열두_자_영숫자다() {
        for (int i = 0; i < 200; i++) {
            var value = PublicId.generate().value();

            assertThat(value).hasSize(12).matches("[A-Za-z0-9]{12}");
        }
    }

    @Test
    void 발급한_공개_ID는_검증을_통과한다() {
        var generated = PublicId.generate();

        assertThatCode(() -> PublicId.of(generated.value())).doesNotThrowAnyException();
    }

    @Test
    void 같은_값의_공개_ID는_같다() {
        assertThat(PublicId.of("aB3dE6gH9jK1")).isEqualTo(PublicId.of("aB3dE6gH9jK1"));
    }

}
