package org.kwakmunsu.auddum.poll.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.poll.application.dto.NewPoll;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.support.stereotype.CommandService;

@Slf4j
@RequiredArgsConstructor
@CommandService
public class PollCommandService {

    private final PollJpaRepository pollRepository;

    /** 공개 ID가 겹칠 때 다시 발급하는 최대 횟수. 12자 영숫자라 겹칠 일이 거의 없어 넘기면 서버 오류다. */
    static final int MAX_PUBLIC_ID_ATTEMPTS = 5;

    /**
     * 새로운 투표를 생성합니다. 입력 검증, 직전 동일 질문(409), 생성 간격(429) 순서로 판단합니다.
     *
     * @param newPoll 새로운 투표 정보
     * @param authorId 투표 작성자 ID
     * @return 생성된 투표
     */
    public Poll createPoll(NewPoll newPoll, Long authorId) {
        LocalDateTime now = LocalDateTime.now(TimeConfig.KST);
        Poll poll = createWithUniquePublicId(newPoll, authorId, now);

        // 직전 투표를 찾아 생성 간격을 확인한다. 없으면 처음 생성이므로 통과.
        pollRepository.findFirstByAuthorIdOrderByIdDesc(authorId)
            .ifPresent(latest -> poll.ensureCreatableAfter(latest, now));

        Poll saved = pollRepository.save(poll);

        log.info("[PollCommandService] Poll 생성 완료 - pollId={}, authorId={}", saved.getId(), authorId);

        return saved;
    }

    /**
     * 공개 ID가 이미 있으면 새로 발급해 다시 만든다(도메인 모델 §8). 확인과 저장 사이에 같은 ID가 들어오는 경쟁은
     * DB의 유니크 제약이 마지막으로 막는다.
     */
    private Poll createWithUniquePublicId(NewPoll newPoll, Long authorId, LocalDateTime now) {
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

            log.warn("[PollCommandService] 공개 ID 충돌, 다시 발급 - attempt={}", attempt + 1);
        }

        throw new AuddumException(DefaultErrorCode.INTERNAL_ERROR);
    }

}
