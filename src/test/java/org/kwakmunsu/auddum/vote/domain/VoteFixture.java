package org.kwakmunsu.auddum.vote.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.springframework.test.util.ReflectionTestUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class VoteFixture {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0, 0);
    static final Long POLL_ID = 10L;
    static final Long FIRST_OPTION_ID = 101L;
    static final Long SECOND_OPTION_ID = 102L;
    static final Long MEMBER_ID = 7L;

    static Poll pollWithoutCloses() {
        return withIds(Poll.create(1L, "점심 뭐 먹지?", Category.ETC, List.of("국밥", "파스타"), null, NOW));
    }

    static Poll pollClosingAt(LocalDateTime closesAt) {
        return withIds(Poll.create(1L, "점심 뭐 먹지?", Category.ETC, List.of("국밥", "파스타"), closesAt, NOW));
    }

    static Poll hiddenPoll() {
        var poll = pollWithoutCloses();
        poll.hide();
        return poll;
    }

    static Poll deletedPoll() {
        var poll = pollWithoutCloses();
        poll.deleteByAdmin();
        return poll;
    }

    static Voter memberVoter() {
        return Voter.member(MEMBER_ID);
    }

    static AnonId anyAnonId() {
        return new AnonId(UUID.randomUUID());
    }

    private static Poll withIds(Poll poll) {
        ReflectionTestUtils.setField(poll, "id", POLL_ID);
        ReflectionTestUtils.setField(poll.getOptions().get(0), "id", FIRST_OPTION_ID);
        ReflectionTestUtils.setField(poll.getOptions().get(1), "id", SECOND_OPTION_ID);
        return poll;
    }

}
