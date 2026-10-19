package org.kwakmunsu.auddum.poll.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.poll.domain.PublicId;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.support.stereotype.QueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

@RequiredArgsConstructor
@QueryService
public class PollQueryService {

    private final PollJpaRepository pollRepository;
    private final MemberReader memberReader;

    /** 공개 투표를 최신순으로 size개 돌려준다. cursorId(마지막으로 본 내부 id)가 없으면 첫 페이지다. */
    public Slice<Poll> getPolls(Long cursorId, int size) {
        long beforeId = cursorId == null ? Long.MAX_VALUE : cursorId;

        return pollRepository.findOlderThan(PollStatus.PUBLIC, beforeId, PageRequest.ofSize(size));
    }

    /**
     * 투표 한 건을 돌려준다. 숨김·삭제 투표는 작성자와 관리자에게만 보이고, 그 밖에는 없는 투표와 같이 404다.
     * 비회원은 viewerId가 null이다. 관리자는 DB의 role이 ADMIN인 회원이다.
     */
    public Poll getPoll(String publicId, Long viewerId) {
        if (!PublicId.isValid(publicId)) {
            throw new AuddumException(DefaultErrorCode.MALFORMED_REQUEST);
        }

        return pollRepository.findByPublicId(publicId)
            .filter(poll -> poll.isReadableBy(viewerId, () -> memberReader.isAdmin(viewerId)))
            .orElseThrow(() -> new AuddumException(PollErrorCode.POLL_NOT_FOUND));
    }

}
