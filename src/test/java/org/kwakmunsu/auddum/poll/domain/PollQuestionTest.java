package org.kwakmunsu.auddum.poll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.AuddumException;

class PollQuestionTest {

    @Test
    void 한_글자_질문은_만들_수_있다() {
        assertThat(PollQuestion.of("a").value()).isEqualTo("a");
    }

    @Test
    void 오백_자_질문은_만들_수_있다() {
        assertThat(PollQuestion.of(PollFixture.repeatChar(500)).value()).hasSize(500);
    }

    @Test
    void 오백한_자_질문은_만들_수_없다() {
        assertThatThrownBy(() -> PollQuestion.of(PollFixture.repeatChar(501)))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문은 1~500자여야 합니다.");
    }

    @Test
    void 빈_질문은_만들_수_없다() {
        assertThatThrownBy(() -> PollQuestion.of(""))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문은 1~500자여야 합니다.");
    }

    @Test
    void 줄바꿈이_있는_질문은_만들_수_없다() {
        assertThatThrownBy(() -> PollQuestion.of("첫째 줄\n둘째 줄"))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문에는 줄바꿈을 넣을 수 없습니다.");
    }

    @Test
    void 캐리지리턴이_있는_질문은_만들_수_없다() {
        assertThatThrownBy(() -> PollQuestion.of("첫째 줄\r\n둘째 줄"))
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

    @Test
    void 공백만_있는_질문은_만들_수_없다() {
        assertThatThrownBy(() -> PollQuestion.of("   "))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문은 1~500자여야 합니다.");
    }

    @Test
    void 앞뒤_공백은_제거한_값이_저장된다() {
        assertThat(PollQuestion.of("  점심 뭐 먹지?  ").value()).isEqualTo("점심 뭐 먹지?");
    }

    @Test
    void 앞뒤_공백이_붙은_오백_자_질문은_제거_후_길이로_판단해_만들_수_있다() {
        assertThat(PollQuestion.of(" " + PollFixture.repeatChar(500) + " ").value()).isEqualTo(PollFixture.repeatChar(500));
    }

    @Test
    void 앞뒤_공백이_붙은_오백한_자_질문은_만들_수_없다() {
        assertThatThrownBy(() -> PollQuestion.of(" " + PollFixture.repeatChar(501) + " "))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문은 1~500자여야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가\u000B나", "가\u000C나", "가\u0085나", "가\u2028나", "가\u2029나"})
    void 유니코드_줄_구분자가_있는_질문은_만들_수_없다(String value) {
        assertThatThrownBy(() -> PollQuestion.of(value))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문에는 줄바꿈을 넣을 수 없습니다.");
    }

}
