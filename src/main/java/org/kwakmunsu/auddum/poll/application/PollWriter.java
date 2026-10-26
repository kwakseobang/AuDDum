package org.kwakmunsu.auddum.poll.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.poll.application.dto.NewPoll;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.springframework.stereotype.Component;

/**
 * 투표 쓰기. 공개 ID가 겹치지 않는 투표를 만들고 저장한다.
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
class PollWriter {

    /** 공개 ID가 겹칠 때 다시 발급하는 최대 횟수. 12자 영숫자라 겹칠 일이 거의 없어 넘기면 서버 오류다. */
    static final int MAX_PUBLIC_ID_ATTEMPTS = 5;

    private final PollJpaRepository pollRepository;

    /**
     * 새 투표를 만들되 저장하지는 않는다(저장은 {@link #save}). 공개 ID가 이미 있으면 새로 발급해 다시 만든다(도메인 모델 §8). 확인과 저장 사이에 같은 ID가 들어오는
     * 경쟁은 DB의 유니크 제약이 마지막으로 막는다.
     */
    Poll createWithUniquePublicId(NewPoll newPoll, Long authorId, LocalDateTime now) {
        for (int attempt = 0; attempt < MAX_PUBLIC_ID_ATTEMPTS; attempt++) {
            Poll poll = Poll.create(
                authorId,
                newPoll.question(),
                newPoll.category(),
                newPoll.options(),
                newPoll.closesAt(),
                now
            );
            if (!pollRepository.existsByPublicIdValue(poll.getPublicId().value())) {
                return poll;
            }

            log.warn("[PollWriter] 공개 ID 충돌, 다시 발급 - authorId={}, attempt={}", authorId, attempt + 1);
        }

        log.error("[PollWriter] 공개 ID 발급 실패 - authorId={}, attempts={}", authorId, MAX_PUBLIC_ID_ATTEMPTS);

        throw new IllegalStateException("공개 ID를 발급하지 못했다");
    }

    Poll save(Poll poll) {
        return pollRepository.save(poll);
    }

}
