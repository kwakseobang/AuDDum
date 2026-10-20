package org.kwakmunsu.auddum.vote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollOption;
import org.kwakmunsu.auddum.support.jpa.BaseEntity;

/**
 * 참여 애그리거트 루트. 한 참여자가 한 투표에서 고른 선택의 불변 기록이다. 만든 뒤에는 바꾸거나 지우지 않는다.
 * 같은 참여자의 중복 참여는 DB 유니크 제약이 마지막으로 막는다. 투표와는 ID 값으로만 연결한다.
 */
@Table(name = "vote")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Vote extends BaseEntity {

    @Column(name = "poll_id", nullable = false, updatable = false)
    private Long pollId;

    @Column(name = "option_id", nullable = false, updatable = false)
    private Long optionId;

    @Embedded
    private Voter voter;

    /**
     * 참여 기록을 만든다. 투표가 공개이고 마감 전인지, 선택지가 이 투표의 것인지 확인한 뒤 만든다.
     * 공개가 아니면 404(POLL_NOT_FOUND), 마감되었으면 409(POLL_CLOSED), 없는 선택지면 400(INVALID_OPTION)이다.
     */
    public static Vote cast(
        @NonNull Poll poll,
        int position,
        @NonNull Voter voter,
        @NonNull LocalDateTime now
    ) {
        poll.ensureVotable(now);
        PollOption option = poll.ensureHasOption(position);

        Vote vote = new Vote();

        vote.pollId = poll.getId();
        vote.optionId = option.getId();
        vote.voter = voter;

        return vote;
    }

}
