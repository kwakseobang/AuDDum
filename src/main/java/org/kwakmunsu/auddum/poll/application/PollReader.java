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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/**
 * 투표 읽기. 열람할 수 있는 투표 한 건과 공개 투표 목록을 돌려준다. 다른 도메인(vote 등)도 서비스가 아니라 이 클래스로 투표를 읽는다(다른 도메인에 여는 것은 {@link #getReadable}뿐이다).
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여한다.
 */
@RequiredArgsConstructor
@Component
public class PollReader {

    private final MemberReader memberReader;
    private final PollJpaRepository pollRepository;

    /**
     * 열람할 수 있는 투표 한 건을 돌려준다. 숨김·삭제 투표는 작성자와 관리자에게만 보이고, 그 밖에는 없는 투표와 같이 404다.
     * 비회원은 viewerId가 null이다. 관리자는 DB의 role이 ADMIN인 회원이다.
     */
    public Poll getReadable(String publicId, Long viewerId) {
        if (!PublicId.isValid(publicId)) {
            throw new AuddumException(DefaultErrorCode.MALFORMED_REQUEST);
        }

        return pollRepository.findByPublicId(publicId)
            .filter(poll -> poll.isReadableBy(viewerId, () -> memberReader.isAdmin(viewerId)))
            .orElseThrow(() -> new AuddumException(PollErrorCode.POLL_NOT_FOUND));
    }

    /** 공개 투표를 최신순으로 size개 돌려준다. cursorId(마지막으로 본 내부 id)가 없으면 첫 페이지다. */
    Slice<Poll> findPublicOlderThan(Long cursorId, int size) {
        long beforeId = cursorId == null ? Long.MAX_VALUE : cursorId;

        return pollRepository.findOlderThan(PollStatus.PUBLIC, beforeId, PageRequest.ofSize(size));
    }

}
