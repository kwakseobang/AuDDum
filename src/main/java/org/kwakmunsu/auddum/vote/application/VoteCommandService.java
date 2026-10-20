package org.kwakmunsu.auddum.vote.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.application.PollQueryService;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.CommandService;
import org.kwakmunsu.auddum.vote.application.dto.CastResult;
import org.kwakmunsu.auddum.vote.application.dto.PollChoice;
import org.kwakmunsu.auddum.vote.application.dto.VoterCredential;

/** 투표 참여(인증-중복-참여 §3). 서비스는 처리 순서만 잇고, 규칙의 실행은 구현 클래스에 시킨다. */
@RequiredArgsConstructor
@CommandService
public class VoteCommandService {

    private final PollQueryService pollQueryService;
    private final VoterResolver voterResolver;
    private final VoteParticipator voteParticipator;

    /**
     * 투표에 참여한다. 투표 조회(없거나 공개가 아니면 404, 형식 오류 400) → 참여자 결정(정지 403, 탈퇴·없음 401)
     * → 참여 처리(기존 참여 409, 마감 409, 선택지 400, 저장) 순서다. 마감 여부는 요청을 받은 시각 한 번으로 판단한다.
     *
     * @param choice     어느 투표의 몇 번 선택지인지
     * @param credential 참여하려는 사람의 신원 단서(회원 ID, 익명 쿠키)
     */
    public CastResult castVote(PollChoice choice, VoterCredential credential) {
        LocalDateTime now = LocalDateTime.now(TimeConfig.KST);

        Poll poll = pollQueryService.getPoll(choice.pollPublicId(), null);
        ResolvedVoter voter = voterResolver.resolve(credential);

        return voteParticipator.participate(poll, voter, choice.position(), now);
    }

}
