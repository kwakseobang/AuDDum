package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.NOW;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 처리 통합 테스트(H2). 기준은 참여 처리 순서, P-08·P-12:
 * 기록이 없으면 저장하고 결과를 돌려주며, 이미 참여했으면 선택과 관계없이(같은 선택, 마감 후 포함) 409 ALREADY_VOTED이고
 * 기존 기록은 그대로다. 참여 우선순위(이미 참여 > 마감 > 선택지 범위)는 이 클래스가 정본이고, 회원 상태·공개 ID 조회의 오류 경로는
 * VoteCommandServiceTest가 보장한다.
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

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void 이미_참여했으면_선택과_관계없이_ALREADY_VOTED이고_기존_기록은_그대로다(int position) {
        // 1: 다른 선택, 2: 같은 선택, 3: 존재하지 않는 선택지(기존 참여가 선택지 검증보다 먼저다)
        var voter = Voter.member(saveMember());
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, voter);

        assertThatThrownBy(() -> voteParticipator.participate(poll, ResolvedVoter.existing(voter), position, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());

        assertThat(voteJpaRepository.findAll()).extracting(Vote::getOptionId)
            .containsExactly(poll.getOptions().get(1).getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void 마감_후에도_이미_참여했으면_POLL_CLOSED가_아니라_ALREADY_VOTED다(int position) {
        var voter = Voter.member(saveMember());
        var poll = savePoll(VoteFixture.pollClosingAt(saveMember(), NOW));
        saveVote(poll, 1, voter);

        assertThatThrownBy(() -> voteParticipator.participate(poll, ResolvedVoter.existing(voter), position, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void 참여_기록이_없는_사람이_마감_시각에_보내면_POLL_CLOSED다(int position) {
        // 3: 존재하지 않는 선택지여도 마감이 선택지 검증보다 먼저다
        var poll = savePoll(VoteFixture.pollClosingAt(saveMember(), NOW));

        var resolved = ResolvedVoter.existing(Voter.member(saveMember()));

        assertThatThrownBy(() -> voteParticipator.participate(poll, resolved, position, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_CLOSED.getMessage());

        assertThat(voteJpaRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 새로_발급한_쿠키_값만_결과에_담긴다(boolean issued) {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var voter = Voter.member(saveMember());
        var resolved = issued ? ResolvedVoter.issued(voter, ISSUED_COOKIE) : ResolvedVoter.existing(voter);

        var result = voteParticipator.participate(poll, resolved, 1, NOW);

        assertThat(result.newCookieValue()).isEqualTo(issued ? ISSUED_COOKIE : null);
    }

}
