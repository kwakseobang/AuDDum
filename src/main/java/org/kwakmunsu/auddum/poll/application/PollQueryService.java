package org.kwakmunsu.auddum.poll.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.application.dto.Participation;
import org.kwakmunsu.auddum.poll.application.dto.PollDetail;
import org.kwakmunsu.auddum.poll.application.dto.PollSummary;
import org.kwakmunsu.auddum.poll.application.dto.Viewer;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.QueryService;
import org.springframework.data.domain.Slice;

@RequiredArgsConstructor
@QueryService
public class PollQueryService {

    private final PollReader pollReader;
    private final ParticipationReader participationReader;

    /**
     * 투표 상세에 참여 정보를 붙인다. 열람 규칙은 {@link PollReader#getReadable}과 같고(숨김·삭제 투표는 작성자와 관리자에게만),
     * 회원 상태는 확인하지 않는다. 결과는 마감되었거나 내가 참여했을 때만 공개하고, 아니면 null이며 전체 참여 수만 내려준다.
     * 마감 여부와 결과 공개는 요청을 받은 시각 한 번으로 판단한다.
     *
     * @param publicId 투표 공개 ID
     * @param viewer   조회하는 사람의 신원 단서(회원 ID, 익명 쿠키)
     */
    public PollDetail getPollDetail(String publicId, Viewer viewer) {
        LocalDateTime now = LocalDateTime.now(TimeConfig.KST);

        Poll poll = pollReader.getReadable(publicId, viewer.memberId());
        Participation participation = participationReader.readFor(poll, viewer);

        return PollDetail.of(poll, participation, now);
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

        Map<Long, Long> counts = participationReader.countByPollIds(pollIds);

        return polls.map(poll -> new PollSummary(poll, counts.get(poll.getId())));
    }

}
