package org.kwakmunsu.auddum.poll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.AuddumException;

class OptionContentTest {

    @Test
    void 한_글자_선택지는_만들_수_있다() {
        assertThat(OptionContent.of("a").value()).isEqualTo("a");
    }

    @Test
    void 오십_자_선택지는_만들_수_있다() {
        assertThat(OptionContent.of(PollFixture.repeatChar(50)).value()).hasSize(50);
    }

    @Test
    void 오십한_자_선택지는_만들_수_없다() {
        assertThatThrownBy(() -> OptionContent.of(PollFixture.repeatChar(51)))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 1~50자여야 합니다.");
    }

    @Test
    void 빈_선택지는_만들_수_없다() {
        assertThatThrownBy(() -> OptionContent.of(""))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 1~50자여야 합니다.");
    }

    @Test
    void 줄바꿈이_있는_선택지는_만들_수_없다() {
        assertThatThrownBy(() -> OptionContent.of("가\n나"))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지에는 줄바꿈을 넣을 수 없습니다.");
    }

    @Test
    void 공백만_있는_선택지은_만들_수_없다() {
        assertThatThrownBy(() -> OptionContent.of("   "))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 1~50자여야 합니다.");
    }

    @Test
    void 앞뒤_공백은_제거한_값이_저장된다() {
        assertThat(OptionContent.of("  국밥  ").value()).isEqualTo("국밥");
    }

    @Test
    void 앞뒤_공백이_붙은_오십_자_선택지은_제거_후_길이로_판단해_만들_수_있다() {
        assertThat(OptionContent.of(" " + PollFixture.repeatChar(50) + " ").value()).isEqualTo(PollFixture.repeatChar(50));
    }

    @Test
    void 앞뒤_공백이_붙은_오십한_자_선택지은_만들_수_없다() {
        assertThatThrownBy(() -> OptionContent.of(" " + PollFixture.repeatChar(51) + " "))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 1~50자여야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가\u000B나", "가\u000C나", "가\u0085나", "가\u2028나", "가\u2029나"})
    void 유니코드_줄_구분자가_있는_선택지은_만들_수_없다(String value) {
        assertThatThrownBy(() -> OptionContent.of(value))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지에는 줄바꿈을 넣을 수 없습니다.");
    }

}
