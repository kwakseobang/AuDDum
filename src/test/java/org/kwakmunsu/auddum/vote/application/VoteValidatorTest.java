package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.NOW;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;

/**
 * 기존 참여 검증 단위 테스트(목 VoteReader). 기준은 API-명세 §4와 예외-정책 P-08·P-12:
 * 이미 참여한 기록이 있으면 선택과 관계없이 409 ALREADY_VOTED이고, 기록이 없으면 통과한다.
 */
class VoteValidatorTest {

    private final VoteReader voteReader = mock(VoteReader.class);
    private final VoteValidator voteValidator = new VoteValidator(voteReader);
    private final Poll poll = VoteFixture.withIds(VoteFixture.openPoll(1L));
    private final Voter voter = Voter.member(7L);

    @Test
    void 참여한_기록이_없으면_통과한다() {
        given(voteReader.find(poll, voter)).willReturn(Optional.empty());

        assertThatCode(() -> voteValidator.validateNotParticipated(poll, voter)).doesNotThrowAnyException();
    }

    @Test
    void 참여한_기록이_있으면_ALREADY_VOTED다() {
        given(voteReader.find(poll, voter)).willReturn(Optional.of(Vote.cast(poll, 1, voter, NOW)));

        assertThatThrownBy(() -> voteValidator.validateNotParticipated(poll, voter))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());
    }

}
