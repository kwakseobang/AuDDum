package org.kwakmunsu.auddum.poll;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.kwakmunsu.auddum.poll.domain.ClosesAt;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.springframework.test.util.ReflectionTestUtils;

/** 마감된 투표를 만드는 테스트 도우미. 도메인에 상관없이 쓰도록 poll 테스트 패키지에 둔다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClosedPollFixture {

    /** 마감 시각을 이 값으로 바꾼다. 생성 시점에는 과거 마감을 만들 수 없으므로 필드를 직접 먼 과거로 바꾼다. */
    public static final LocalDateTime LONG_PAST = LocalDateTime.of(2000, 1, 1, 0, 0);

    public static Poll expire(Poll poll) {
        ReflectionTestUtils.setField(poll, "closesAt", new ClosesAt(LONG_PAST));

        return poll;
    }

}
