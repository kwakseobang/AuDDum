package org.kwakmunsu.auddum.poll.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.poll.application.dto.NewPoll;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.CommandService;

@Slf4j
@RequiredArgsConstructor
@CommandService
public class PollCommandService {

    private final PollWriter pollWriter;
    private final PollValidator pollValidator;
    private final MemberReader memberReader;

    /**
     * 새로운 투표를 생성합니다. 정상 회원만 만들 수 있고(정지 403, 탈퇴·없음 401), 입력 검증, 직전 동일 질문(409), 생성 간격(429) 순서로 판단합니다.
     *
     * @param newPoll 새로운 투표 정보
     * @param authorId 투표 작성자 ID
     * @return 생성된 투표
     */
    public Poll createPoll(NewPoll newPoll, Long authorId) {
        memberReader.getActiveMember(authorId);

        LocalDateTime now = LocalDateTime.now(TimeConfig.KST);
        Poll poll = pollWriter.createWithUniquePublicId(newPoll, authorId, now);
        pollValidator.ensureCreatable(poll, now);

        Poll saved = pollWriter.save(poll);

        log.info("[PollCommandService] Poll 생성 완료 - pollId={}, authorId={}", saved.getId(), authorId);

        return saved;
    }

}
