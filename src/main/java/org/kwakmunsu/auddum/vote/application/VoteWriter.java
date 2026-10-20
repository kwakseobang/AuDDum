package org.kwakmunsu.auddum.vote.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.VoteJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 참여 기록 쓰기. 저장하고, 저장소가 던지는 유니크 제약 위반을 도메인 오류로 바꾼다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class VoteWriter {

    private final VoteJpaRepository voteRepository;

    /**
     * 참여 기록을 만들어 저장한다. 투표가 공개이고 마감 전인지, 선택지가 이 투표의 것인지는 도메인(`Vote.cast`)이 확인하고 어기면 각각 404, 409, 400이다. 저장에서 유니크 위반이 나면
     * {@link #saveOnce}가 409로 바꾼다.
     */
    public Vote cast(Poll poll, Voter voter, int position, LocalDateTime now) {
        Vote vote = Vote.cast(
            poll,
            position,
            voter,
            now
        );
        saveOnce(vote);

        log.info("[VoteWriter] 참여 완료 - pollId={}, optionId={}, memberId={}",
            vote.getPollId(),
            vote.getOptionId(),
            voter.memberId()
        );

        return vote;
    }

    /**
     * 저장하고 바로 반영한다. 같은 참여자의 기록이 이미 들어와 유니크 제약을 어기면(동시 요청에서 진 쪽) 409 ALREADY_VOTED다. 외래키·CHECK 같은 다른 제약 위반은 진짜 오류이므로 그대로 올린다.
     */
    public void saveOnce(Vote vote) {
        try {
            voteRepository.saveAndFlush(vote);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new AuddumException(VoteErrorCode.ALREADY_VOTED, e);
            }

            throw e;
        }
    }

    private boolean isUniqueViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException cause
            && cause.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
    }

}
