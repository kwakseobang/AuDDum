package org.kwakmunsu.auddum.vote.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.vote.application.dto.CastResult;
import org.springframework.stereotype.Component;

/**
 * 참여 처리. 이미 참여한 사람이 아닌지 확인하고 참여시킨 뒤 결과를 조립한다.
 * 서비스는 이 클래스에 참여를 시키기만 하고, 확인·저장·결과 조립은 구현 클래스들이 맡는다.
 */
@RequiredArgsConstructor
@Component
public class VoteParticipator {

    private final VoteValidator voteValidator;
    private final VoteWriter voteWriter;
    private final VoteReader voteReader;

    /**
     * 이미 참여했으면 선택과 관계없이 409다. 아니면 마감(409), 선택지(400)를 확인한 뒤 저장한다.
     * 같은 참여자의 동시 요청에서 진 쪽도 409다.
     */
    public CastResult participate(Poll poll, ResolvedVoter resolved, int position, LocalDateTime now) {
        voteValidator.validateNotParticipated(poll, resolved.voter());
        voteWriter.cast(poll, resolved.voter(), position, now);

        return new CastResult(position, voteReader.tally(poll), resolved.newCookieValue());
    }

}
