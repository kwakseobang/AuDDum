package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.NOW;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 처리 통합 테스트(H2). 기준은 API-명세 §4, 예외-정책 P-08·P-12:
 * 기록이 없으면 저장하고 결과를 돌려주며, 이미 참여했으면 선택과 관계없이(같은 선택, 마감 후 포함) 409 ALREADY_VOTED이고
 * 기존 기록은 그대로다. 회원 상태·공개 ID 조회의 오류 경로는 VoteCommandServiceTest가 보장한다.
 */
@RequiredArgsConstructor
@Transactional
class VoteParticipatorTest extends VoteIntegrationTestSupport {

    private static final String ISSUED_COOKIE = "v1.issued-cookie";

    final VoteParticipator voteParticipator;

    @Test
    void 기록이_없으면_저장하고_내_선택과_결과를_돌려준다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));
        var voter = Voter.member(saveMember());

        var result = voteParticipator.participate(poll, ResolvedVoter.existing(voter), 2, NOW);

        assertThat(result.myChoice()).isEqualTo(2);
        assertThat(result.result().total()).isEqualTo(2);
        assertThat(result.result().options()).extracting("position", "count")
            .containsExactly(tuple(1, 1L), tuple(2, 1L));
        assertThat(voteJpaRepository.findAll()).extracting(Vote::getVoter).contains(voter);
    }

    @Test
    void 같은_선택을_다시_보내도_ALREADY_VOTED이고_기록은_한_건이다() {
        var voter = Voter.member(saveMember());
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, voter);

        assertThatThrownBy(() -> voteParticipator.participate(poll, ResolvedVoter.existing(voter), 2, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());

        assertThat(voteJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void 다른_선택을_보내면_ALREADY_VOTED이고_기존_기록은_그대로다() {
        var voter = Voter.member(saveMember());
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, voter);

        assertThatThrownBy(() -> voteParticipator.participate(poll, ResolvedVoter.existing(voter), 1, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());

        assertThat(voteJpaRepository.findAll()).extracting(Vote::getOptionId)
            .containsExactly(poll.getOptions().get(1).getId());
    }

    @Test
    void 마감_후_같은_선택을_다시_보내면_POLL_CLOSED가_아니라_ALREADY_VOTED다() {
        var voter = Voter.member(saveMember());
        var poll = savePoll(VoteFixture.pollClosingAt(saveMember(), NOW));
        saveVote(poll, 1, voter);

        assertThatThrownBy(() -> voteParticipator.participate(poll, ResolvedVoter.existing(voter), 1, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());
    }

    @Test
    void 참여_기록이_없는_사람이_마감_시각에_보내면_POLL_CLOSED다() {
        var poll = savePoll(VoteFixture.pollClosingAt(saveMember(), NOW));

        var resolved = ResolvedVoter.existing(Voter.member(saveMember()));

        assertThatThrownBy(() -> voteParticipator.participate(poll, resolved, 1, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_CLOSED.getMessage());

        assertThat(voteJpaRepository.count()).isZero();
    }

    @Test
    void 새로_발급한_쿠키_값은_결과에_그대로_담긴다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var voter = Voter.member(saveMember());

        var result = voteParticipator.participate(poll, ResolvedVoter.issued(voter, ISSUED_COOKIE), 1, NOW);

        assertThat(result.newCookieValue()).isEqualTo(ISSUED_COOKIE);
    }

    @Test
    void 쿠키를_발급하지_않았으면_결과의_쿠키_값은_null이다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        var result = voteParticipator.participate(poll, ResolvedVoter.existing(Voter.member(saveMember())), 1, NOW);

        assertThat(result.newCookieValue()).isNull();
    }

}
