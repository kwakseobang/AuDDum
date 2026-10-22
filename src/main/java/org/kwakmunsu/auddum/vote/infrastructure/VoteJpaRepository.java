package org.kwakmunsu.auddum.vote.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteJpaRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByPollIdAndVoterMemberId(Long pollId, Long memberId);

    Optional<Vote> findByPollIdAndVoterAnonIdValue(Long pollId, UUID anonId);

    /**
     * 이 참여자의 투표 기록. 임베디드 값 전체를 비교하면 비어 있는 쪽(NULL)이 같다고 판단되지 않으므로
     * 회원이면 회원 ID로, 비회원이면 익명 식별자로 찾는다.
     */
    default Optional<Vote> findByPollIdAndVoter(Long pollId, Voter voter) {
        if (voter.memberId() != null) {
            return findByPollIdAndVoterMemberId(pollId, voter.memberId());
        }

        return findByPollIdAndVoterAnonIdValue(pollId, voter.anonId().value());
    }

    /** 선택지별 득표 수. 득표가 없는 선택지는 나오지 않는다. */
    @Query("select v.optionId as optionId, count(v) as votes from Vote v where v.pollId = :pollId group by v.optionId")
    List<OptionCount> countByOption(@Param("pollId") Long pollId);

    /** 투표별 참여 수. 참여가 없는 투표는 나오지 않는다. */
    @Query("select v.pollId as pollId, count(v) as votes from Vote v where v.pollId in :pollIds group by v.pollId")
    List<PollCount> countByPollIds(@Param("pollIds") Collection<Long> pollIds);

    interface PollCount {

        Long getPollId();

        long getVotes();

    }

    interface OptionCount {

        Long getOptionId();

        long getVotes();

    }

}
