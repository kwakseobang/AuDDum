package org.kwakmunsu.auddum.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.support.error.AuddumException;

class PollTakedownTest {

    @Test
    void 숨김은_이력_종류가_HIDE_POLL이고_공개_투표를_숨긴다() {
        var poll = publicPoll();

        PollTakedown.HIDDEN.apply(poll);

        assertThat(PollTakedown.HIDDEN.actionType()).isEqualTo(ActionType.HIDE_POLL);
        assertThat(poll.getStatus()).isEqualTo(PollStatus.HIDDEN);
    }

    @Test
    void 삭제는_이력_종류가_DELETE_POLL이고_공개_투표를_삭제한다() {
        var poll = publicPoll();

        PollTakedown.DELETED.apply(poll);

        assertThat(PollTakedown.DELETED.actionType()).isEqualTo(ActionType.DELETE_POLL);
        assertThat(poll.getStatus()).isEqualTo(PollStatus.DELETED);
    }

    @Test
    void 숨김_투표는_삭제할_수_있다() {
        var poll = publicPoll();
        PollTakedown.HIDDEN.apply(poll);

        PollTakedown.DELETED.apply(poll);

        assertThat(poll.getStatus()).isEqualTo(PollStatus.DELETED);
    }

    @Test
    void 이미_숨긴_투표를_숨기면_409_POLL_NOT_PUBLIC이다() {
        var poll = publicPoll();
        PollTakedown.HIDDEN.apply(poll);

        assertThatThrownBy(() -> PollTakedown.HIDDEN.apply(poll))
            .isInstanceOfSatisfying(AuddumException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PollErrorCode.POLL_NOT_PUBLIC));
    }

    @Test
    void 삭제된_투표는_숨기거나_삭제하면_404_POLL_NOT_FOUND다() {
        var poll = publicPoll();
        PollTakedown.DELETED.apply(poll);

        for (var takedown : PollTakedown.values()) {
            assertThatThrownBy(() -> takedown.apply(poll))
                .isInstanceOfSatisfying(AuddumException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(PollErrorCode.POLL_NOT_FOUND));
        }
        assertThat(poll.getStatus()).isEqualTo(PollStatus.DELETED);
    }

    private Poll publicPoll() {
        return Poll.create(1L, "점심 뭐 먹지?", Category.ETC, List.of("국밥", "파스타"), null, LocalDateTime.of(2026, 10, 13, 12, 0));
    }

}
