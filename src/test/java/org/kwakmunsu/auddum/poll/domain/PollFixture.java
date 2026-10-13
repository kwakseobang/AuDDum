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

    static Poll 마감_없는_투표() {
        return Poll.create(AUTHOR_ID, "점심 뭐 먹지?", Category.ETC, List.of("국밥", "파스타"), null, NOW);
    }

    static Poll 마감_있는_투표(LocalDateTime closesAt) {
        return Poll.create(AUTHOR_ID, "점심 뭐 먹지?", Category.ETC, List.of("국밥", "파스타"), closesAt, NOW);
    }

    static Poll 숨김_투표() {
        var poll = 마감_없는_투표();
        poll.hide();
        return poll;
    }

    static Poll 삭제된_투표() {
        var poll = 마감_없는_투표();
        poll.deleteByAuthor(AUTHOR_ID);
        return poll;
    }

    static String 글자(int count) {
        return "가".repeat(count);
    }

}
