package org.kwakmunsu.auddum.poll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.support.error.AuddumException;

class OptionContentTest {

    @ParameterizedTest
    @MethodSource("만들_수_있는_선택지")
    void 앞뒤_공백을_제거한_길이가_1에서_50자이면_제거한_값으로_만들_수_있다(String input, String expected) {
        assertThat(OptionContent.of(input).value()).isEqualTo(expected);
    }

    static Stream<Arguments> 만들_수_있는_선택지() {
        return Stream.of(
                Arguments.of("a", "a"),
                Arguments.of(PollFixture.repeatChar(50), PollFixture.repeatChar(50)),
                Arguments.of("  국밥  ", "국밥"),
                Arguments.of(" " + PollFixture.repeatChar(50) + " ", PollFixture.repeatChar(50))
        );
    }

    @ParameterizedTest
    @MethodSource("길이가_범위_밖인_선택지")
    void 앞뒤_공백을_제거한_길이가_1에서_50자를_벗어나면_만들_수_없다(String input) {
        assertThatThrownBy(() -> OptionContent.of(input))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 1~50자여야 합니다.");
    }

    static Stream<String> 길이가_범위_밖인_선택지() {
        return Stream.of(
                "",                                              // 빈 선택지
                "   ",                                           // 공백만
                PollFixture.repeatChar(51),                      // 오십한 자
                " " + PollFixture.repeatChar(51) + " "           // 앞뒤 공백을 제거해도 오십한 자
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"가\n나", "가\u000B나", "가\u000C나", "가\u0085나", "가 나", "가 나"})
    void 줄바꿈이나_유니코드_줄_구분자가_있으면_만들_수_없다(String value) {
        assertThatThrownBy(() -> OptionContent.of(value))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지에는 줄바꿈을 넣을 수 없습니다.");
    }

}
