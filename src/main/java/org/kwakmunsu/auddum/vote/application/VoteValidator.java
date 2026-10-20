package org.kwakmunsu.auddum.vote.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.stereotype.Component;

/** 참여 규칙 검증. 서비스가 시키면 규칙을 확인하고 어기면 도메인 오류를 던진다. */
@RequiredArgsConstructor
@Component
public class VoteValidator {

    private final VoteReader voteReader;

    /** 이미 참여한 사람이면 선택과 관계없이 409(ALREADY_VOTED)다. 마감 후에도 같다. */
    public void ensureNotParticipated(Poll poll, Voter voter) {
        if (voteReader.find(poll, voter).isPresent()) {
            throw new AuddumException(VoteErrorCode.ALREADY_VOTED);
        }
    }

}
