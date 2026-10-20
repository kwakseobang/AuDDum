package org.kwakmunsu.auddum.vote.application;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.application.PollQueryService;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.QueryService;
import org.kwakmunsu.auddum.vote.application.dto.PollDetail;
import org.kwakmunsu.auddum.vote.application.dto.VoterCredential;
import org.kwakmunsu.auddum.vote.domain.Voter;

/** 참여 정보가 붙는 투표 조회(API-명세 §3·§4). 쿠키를 읽기만 하고 발급하지 않는다. */
@RequiredArgsConstructor
@QueryService
public class VoteQueryService {

    private final PollQueryService pollQueryService;
    private final VoterResolver voterResolver;
    private final VoteReader voteReader;

    /**
     * 투표 상세에 내 선택과 결과를 붙인다. 열람 규칙은 {@link PollQueryService#getPoll}과 같고(숨김·삭제 투표는 작성자와 관리자에게만),
     * 회원 상태는 확인하지 않는다. 결과는 마감되었거나 내가 참여했을 때만 공개하고, 아니면 null이며 전체 참여 수만 내려준다.
     *
     * @param pollPublicId 투표 공개 ID
     * @param credential   조회하는 사람의 신원 단서(회원 ID, 익명 쿠키)
     */
    public PollDetail getPollDetail(String pollPublicId, VoterCredential credential) {
        LocalDateTime now = LocalDateTime.now(TimeConfig.KST);

        Poll poll = pollQueryService.getPoll(pollPublicId, credential.memberId());
        Optional<Voter> voter = voterResolver.find(credential);

        return voteReader.readDetail(poll, voter, now);
    }

}
