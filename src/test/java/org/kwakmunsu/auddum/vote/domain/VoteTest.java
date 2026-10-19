package org.kwakmunsu.auddum.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.vote.domain.VoteFixture.FIRST_OPTION_ID;
import static org.kwakmunsu.auddum.vote.domain.VoteFixture.NOW;
import static org.kwakmunsu.auddum.vote.domain.VoteFixture.POLL_ID;
import static org.kwakmunsu.auddum.vote.domain.VoteFixture.SECOND_OPTION_ID;
import static org.kwakmunsu.auddum.vote.domain.VoteFixture.anyAnonId;
import static org.kwakmunsu.auddum.vote.domain.VoteFixture.memberVoter;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.support.error.AuddumException;

class VoteTest {

    // ---- 생성 ----

    @Test
    void 회원이_참여하면_투표와_선택지와_참여자가_담긴다() {
        var voter = memberVoter();

        var vote = Vote.cast(VoteFixture.pollWithoutCloses(), 1, voter, NOW);

        assertThat(vote.getPollId()).isEqualTo(POLL_ID);
        assertThat(vote.getOptionId()).isEqualTo(FIRST_OPTION_ID);
        assertThat(vote.getVoter()).isEqualTo(voter);
    }

    @Test
    void 익명으로_참여하면_익명_참여자가_담긴다() {
        var voter = Voter.anonymous(anyAnonId());

        var vote = Vote.cast(VoteFixture.pollWithoutCloses(), 2, voter, NOW);

        assertThat(vote.getOptionId()).isEqualTo(SECOND_OPTION_ID);
        assertThat(vote.getVoter()).isEqualTo(voter);
    }

    @Test
    void 투표에_없는_순번으로_참여할_수_없다() {
        assertThatThrownBy(() -> Vote.cast(VoteFixture.pollWithoutCloses(), 3, memberVoter(), NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.INVALID_OPTION.getMessage());
    }

    @Test
    void 순번_0으로_참여할_수_없다() {
        assertThatThrownBy(() -> Vote.cast(VoteFixture.pollWithoutCloses(), 0, memberVoter(), NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.INVALID_OPTION.getMessage());
    }

    @Test
    void 숨김_투표에는_참여할_수_없다() {
        assertThatThrownBy(() -> Vote.cast(VoteFixture.hiddenPoll(), 1, memberVoter(), NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 삭제된_투표에는_참여할_수_없다() {
        assertThatThrownBy(() -> Vote.cast(VoteFixture.deletedPoll(), 1, memberVoter(), NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    // ---- 마감 경계 ----

    @Test
    void 마감_시각_직전에는_참여할_수_있다() {
        var closesAt = NOW.plusHours(1);
        var poll = VoteFixture.pollClosingAt(closesAt);

        assertThatCode(() -> Vote.cast(poll, 1, memberVoter(), closesAt.minusSeconds(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void 마감_시각과_같으면_참여할_수_없다() {
        var closesAt = NOW.plusHours(1);
        var poll = VoteFixture.pollClosingAt(closesAt);

        assertThatThrownBy(() -> Vote.cast(poll, 1, memberVoter(), closesAt))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_CLOSED.getMessage());
    }

    @Test
    void 마감_시각_직후에는_참여할_수_없다() {
        var closesAt = NOW.plusHours(1);
        var poll = VoteFixture.pollClosingAt(closesAt);

        assertThatThrownBy(() -> Vote.cast(poll, 1, memberVoter(), closesAt.plusSeconds(1)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_CLOSED.getMessage());
    }

    @Test
    void 마감_시각이_없는_투표에는_언제든_참여할_수_있다() {
        assertThatCode(() -> Vote.cast(VoteFixture.pollWithoutCloses(), 1, memberVoter(), NOW.plusYears(10)))
                .doesNotThrowAnyException();
    }

    // ---- isSameChoice ----

    @Test
    void 같은_선택지면_같은_선택이다() {
        var vote = Vote.cast(VoteFixture.pollWithoutCloses(), 1, memberVoter(), NOW);

        assertThat(vote.isSameChoice(FIRST_OPTION_ID)).isTrue();
    }

    @Test
    void 다른_선택지면_같은_선택이_아니다() {
        var vote = Vote.cast(VoteFixture.pollWithoutCloses(), 1, memberVoter(), NOW);

        assertThat(vote.isSameChoice(SECOND_OPTION_ID)).isFalse();
    }

    // ---- 불변성 ----

    @Test
    void 참여_기록에는_값을_바꾸는_공개_메서드가_없다() {
        var mutators = Arrays.stream(Vote.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
                .map(Method::getName)
                .filter(name -> !name.startsWith("get") && !name.equals("isSameChoice"))
                .toList();

        assertThat(mutators).isEmpty();
    }

}
