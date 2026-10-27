package org.kwakmunsu.auddum.vote;

import static org.kwakmunsu.auddum.vote.application.VoteFixture.CREATED_AT;

import java.time.LocalDateTime;
import org.kwakmunsu.auddum.DataIntegrationTestSupport;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.VoteJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 참여 통합 테스트의 공통 저장 헬퍼. 회원·투표 저장은 {@link DataIntegrationTestSupport}에 있고, 여기에는 참여 기록 저장만 둔다.
 * 저장소 없이 쓰는 값(투표 데이터, 쿠키 조작)은 {@code VoteFixture}에 있다.
 */
public abstract class VoteIntegrationTestSupport extends DataIntegrationTestSupport {

    @Autowired
    protected VoteJpaRepository voteJpaRepository;

    protected void saveVote(Poll poll, int position, Voter voter) {
        saveVote(poll, position, voter, CREATED_AT);
    }

    protected void saveVote(Poll poll, int position, Voter voter, LocalDateTime castAt) {
        voteJpaRepository.saveAndFlush(Vote.cast(poll, position, voter, castAt));
    }

}
