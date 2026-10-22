package org.kwakmunsu.auddum.vote.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.application.ParticipationReader;
import org.kwakmunsu.auddum.poll.application.dto.Participation;
import org.kwakmunsu.auddum.poll.application.dto.Viewer;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.vote.application.dto.VoterCredential;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.stereotype.Component;

/**
 * poll이 선언한 {@link ParticipationReader}의 구현(의존 방향 {@code vote → poll}을 지킨다). 조회하는 사람의 신원 단서를 참여자로 바꾸고
 * 참여 기록을 읽어 사실(참여 수, 내 선택, 득표)만 돌려준다. 결과를 공개할지는 poll이 정한다.
 */
@RequiredArgsConstructor
@Component
public class VoteParticipationReader implements ParticipationReader {

    private final VoterResolver voterResolver;
    private final VoteReader voteReader;

    @Override
    public Map<Long, Long> countByPollIds(Collection<Long> pollIds) {
        return voteReader.countByPollIds(pollIds);
    }

    @Override
    public Participation readFor(Poll poll, Viewer viewer) {
        Optional<Voter> voter = voterResolver.find(new VoterCredential(viewer.memberId(), viewer.anonCookieValue()));

        Integer myChoice = voter.flatMap(found -> voteReader.findChoice(poll, found)).orElse(null);

        return new Participation(myChoice, voteReader.tally(poll));
    }

}
