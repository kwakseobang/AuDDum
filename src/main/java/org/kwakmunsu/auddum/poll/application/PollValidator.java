package org.kwakmunsu.auddum.poll.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.springframework.stereotype.Component;

/** 투표 생성 규칙 검증. 서비스가 시키면 규칙을 확인하고 어기면 도메인 오류를 던진다. */
@RequiredArgsConstructor
@Component
class PollValidator {

    private final PollReader pollReader;

    /**
     * 작성자의 직전 투표와 견주어 만들 수 있는지 확인한다. 직전과 같은 질문이면 409, 생성 간격이 짧으면 429다.
     * 직전 투표가 없으면 처음 만드는 것이므로 통과한다.
     */
    void ensureCreatable(Poll poll, LocalDateTime now) {
        pollReader.findLatestByAuthor(poll.getAuthorId())
            .ifPresent(latest -> poll.ensureCreatableAfter(latest, now));
    }

}
