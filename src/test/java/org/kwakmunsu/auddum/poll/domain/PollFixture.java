package org.kwakmunsu.auddum.poll.domain;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PollFixture {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0, 0);
    static final Long AUTHOR_ID = 1L;
    static final Long OTHER_MEMBER_ID = 2L;

    static Poll pollWithoutCloses() {
        return Poll.create(AUTHOR_ID, "점심 뭐 먹지?", Category.ETC, List.of("국밥", "파스타"), null, NOW);
    }

    static Poll pollWithCloses(LocalDateTime closesAt) {
        return Poll.create(AUTHOR_ID, "점심 뭐 먹지?", Category.ETC, List.of("국밥", "파스타"), closesAt, NOW);
    }

    static Poll hiddenPoll() {
        var poll = pollWithoutCloses();
        poll.hide();
        return poll;
    }

    static Poll deletedPoll() {
        var poll = pollWithoutCloses();
        poll.deleteByAuthor(AUTHOR_ID);
        return poll;
    }

    static String repeatChar(int count) {
        return "가".repeat(count);
    }

}
