package org.kwakmunsu.auddum.vote.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.application.dto.OptionTally;
import org.kwakmunsu.auddum.poll.application.dto.PollTally;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.VoteJpaRepository;
import org.kwakmunsu.auddum.vote.infrastructure.VoteJpaRepository.OptionCount;
import org.kwakmunsu.auddum.vote.infrastructure.VoteJpaRepository.PollCount;
import org.springframework.stereotype.Component;

/**
 * 참여 기록 읽기. 이 참여자의 기록과 내 선택, 투표 결과를 돌려준다. 결과는 항상 투표 기록(vote)에서 세므로 집계와 기록이 어긋날 수 없다.
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여한다.
 */
@RequiredArgsConstructor
@Component
public class VoteReader {

    private final VoteJpaRepository voteRepository;

    /** 이 참여자가 이 투표에 남긴 기록. 참여하지 않았으면 비어 있다. */
    public Optional<Vote> find(Poll poll, Voter voter) {
        return voteRepository.findByPollIdAndVoter(poll.getId(), voter);
    }

    /** 이 참여자가 고른 선택지 순번. 참여하지 않았으면 비어 있다. */
    public Optional<Integer> findChoice(Poll poll, Voter voter) {
        return find(poll, voter).map(vote -> poll.positionOf(vote.getOptionId()));
    }

    /** 투표별 참여 수를 한 번에 센다(투표 목록용). 참여가 없는 투표는 0이다. */
    public Map<Long, Long> countByPollIds(Collection<Long> pollIds) {
        if (pollIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> counts = voteRepository.countByPollIds(pollIds).stream()
            .collect(Collectors.toMap(PollCount::getPollId, PollCount::getVotes));

        return pollIds.stream().collect(Collectors.toMap(Function.identity(), id -> counts.getOrDefault(id, 0L), (a, b) -> a));
    }

    /** 선택지별 득표 수. 득표가 없는 선택지도 0으로 포함하고 순번 오름차순이다. */
    public PollTally tally(Poll poll) {
        Map<Long, Long> countsByOption = voteRepository.countByOption(poll.getId()).stream()
            .collect(Collectors.toMap(OptionCount::getOptionId, OptionCount::getVotes));

        List<OptionTally> options = poll.getOptions().stream()
            .map(option -> OptionTally.of(option.getPosition().value(), countsByOption.getOrDefault(option.getId(), 0L)))
            .toList();
        long total = options.stream().mapToLong(OptionTally::count).sum();

        return PollTally.of(total, options);
    }

}
