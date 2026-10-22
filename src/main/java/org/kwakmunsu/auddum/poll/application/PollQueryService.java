package org.kwakmunsu.auddum.poll.application;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.application.dto.PollSummary;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.stereotype.QueryService;
import org.springframework.data.domain.Slice;

@RequiredArgsConstructor
@QueryService
public class PollQueryService {

    private final PollReader pollReader;
    private final ParticipantCounter participantCounter;

    /** 열람할 수 있는 투표 한 건을 돌려준다. 규칙은 {@link PollReader#getReadable}과 같다. */
    public Poll getPoll(String publicId, Long viewerId) {
        return pollReader.getReadable(publicId, viewerId);
    }

    /**
     * 공개 투표 목록에 참여 수를 붙인다. 목록 조회는 {@link PollReader#findPublicOlderThan}과 같고, 그 페이지의 투표들을 한 번에 집계한다.
     * 결과 공개 규칙과 무관하게 전체 참여 수는 항상 내려준다.
     */
    public Slice<PollSummary> getPollSummaries(Long cursorId, int size) {
        Slice<Poll> polls = pollReader.findPublicOlderThan(cursorId, size);

        List<Long> pollIds = polls.getContent()
            .stream()
            .map(Poll::getId)
            .toList();

        Map<Long, Long> counts = participantCounter.countByPollIds(pollIds);

        return polls.map(poll -> new PollSummary(poll, counts.get(poll.getId())));
    }

}
