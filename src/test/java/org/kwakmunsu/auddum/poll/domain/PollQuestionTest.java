package org.kwakmunsu.auddum.poll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.support.error.AuddumException;

class PollQuestionTest {

    @ParameterizedTest
    @MethodSource("만들_수_있는_질문")
    void 앞뒤_공백을_제거한_길이가_1에서_500자이면_제거한_값으로_만들_수_있다(String input, String expected) {
        assertThat(PollQuestion.of(input).value()).isEqualTo(expected);
    }

    static Stream<Arguments> 만들_수_있는_질문() {
        return Stream.of(
                Arguments.of("a", "a"),
                Arguments.of(PollFixture.repeatChar(500), PollFixture.repeatChar(500)),
                Arguments.of("  점심 뭐 먹지?  ", "점심 뭐 먹지?"),
                Arguments.of(" " + PollFixture.repeatChar(500) + " ", PollFixture.repeatChar(500))
        );
    }

    @ParameterizedTest
    @MethodSource("길이가_범위_밖인_질문")
    void 앞뒤_공백을_제거한_길이가_1에서_500자를_벗어나면_만들_수_없다(String input) {
        assertThatThrownBy(() -> PollQuestion.of(input))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문은 1~500자여야 합니다.");
    }

    static Stream<String> 길이가_범위_밖인_질문() {
        return Stream.of(
                "",                                              // 빈 질문
                "   ",                                           // 공백만
                PollFixture.repeatChar(501),                     // 오백한 자
                " " + PollFixture.repeatChar(501) + " "          // 앞뒤 공백을 제거해도 오백한 자
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"첫째 줄\n둘째 줄", "첫째 줄\r\n둘째 줄", "가\u000B나", "가\u000C나", "가\u0085나", "가 나", "가 나"})
    void 줄바꿈이나_유니코드_줄_구분자가_있으면_만들_수_없다(String value) {
        assertThatThrownBy(() -> PollQuestion.of(value))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문에는 줄바꿈을 넣을 수 없습니다.");
    }

    @Test
    void 질문_길이는_유니코드_문자_단위로_센다() {
        // 이모지는 UTF-16으로 2칸이지만 문자 1개다. 500개면 통과, 501개면 거부.
        var emoji = "😀";

        assertThat(PollQuestion.of(emoji.repeat(500)).value()).isEqualTo(emoji.repeat(500));
        assertThatThrownBy(() -> PollQuestion.of(emoji.repeat(501)))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문은 1~500자여야 합니다.");
    }

}
