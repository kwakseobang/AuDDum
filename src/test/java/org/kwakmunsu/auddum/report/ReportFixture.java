package org.kwakmunsu.auddum.report;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.kwakmunsu.auddum.poll.ClosedPollFixture;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;

/** 신고 테스트에서 반복되는 투표 데이터와 요청 본문. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReportFixture {

    public static final String ALLOWED_ORIGIN = "http://localhost:3000";
    public static final int DETAIL_MAX = 200;

    public static Poll newPoll(Long authorId) {
        return Poll.create(authorId, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", Category.SOME,
                List.of("관심 없음", "그냥 바쁜 것"), null, LocalDateTime.now(TimeConfig.KST));
    }

    public static Poll newHiddenPoll(Long authorId) {
        var poll = newPoll(authorId);
        poll.hide();

        return poll;
    }

    public static Poll newDeletedPoll(Long authorId) {
        var poll = newPoll(authorId);
        poll.deleteByAdmin();

        return poll;
    }

    public static Poll newClosedPoll(Long authorId) {
        return ClosedPollFixture.expire(newPoll(authorId));
    }

    public static String reasonBody(String reason) {
        return "{\"reason\": \"" + reason + "\"}";
    }

    public static String reasonBody(String reason, String detail) {
        return "{\"reason\": \"" + reason + "\", \"detail\": \"" + detail + "\"}";
    }

}
