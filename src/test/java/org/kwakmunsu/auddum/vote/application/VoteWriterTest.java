package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.VoteJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

/**
 * 참여 기록 쓰기 단위 테스트(목 repository). 기준은 인증-중복-참여 §3 "동시 요청"·"트랜잭션 경계", API-명세 §4 4~6단계:
 * 저장 전에 공개·마감 전(404, 409)과 선택지 소속(400)을 확인하고, 저장에서는 유니크 제약 위반만 409 ALREADY_VOTED로
 * 바꾸며(원인 예외를 잇는다) 외래키·CHECK 위반과 그 밖의 예외는 그대로 올린다.
 * 실제 DB가 만드는 유니크 위반 end-to-end는 VoteWriterConflictTest가 검증한다.
 */
class VoteWriterTest {

    private final VoteJpaRepository voteRepository = mock(VoteJpaRepository.class);
    private final VoteWriter voteWriter = new VoteWriter(voteRepository);
    private final Vote vote = mock(Vote.class);

    // ---- cast: 규칙 확인 뒤 저장 ----

    @Test
    void 참여하면_투표_선택지_참여자를_담은_기록을_저장하고_돌려준다() {
        var poll = VoteFixture.withIds(VoteFixture.openPoll(1L));

        var cast = voteWriter.cast(poll, Voter.member(7L), 2, NOW);

        assertThat(cast.getPollId()).isEqualTo(10L);
        assertThat(cast.getOptionId()).isEqualTo(102L);
        assertThat(cast.getVoter()).isEqualTo(Voter.member(7L));
        verify(voteRepository).saveAndFlush(cast);
    }

    @ParameterizedTest
    @MethodSource("저장하지_않는_위반")
    void 도메인_규칙을_어기면_오류를_던지고_저장하지_않는다(Poll poll, int position, PollErrorCode expected) {
        assertThatThrownBy(() -> voteWriter.cast(poll, Voter.member(7L), position, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(expected.getMessage());

        verify(voteRepository, never()).saveAndFlush(any(Vote.class));
    }

    static Stream<Arguments> 저장하지_않는_위반() {
        var hidden = VoteFixture.withIds(VoteFixture.openPoll(1L));
        hidden.hide();

        return Stream.of(
            Arguments.of(VoteFixture.withIds(VoteFixture.pollClosingAt(1L, NOW)), 1, PollErrorCode.POLL_CLOSED),
            Arguments.of(VoteFixture.withIds(VoteFixture.openPoll(1L)), 3, PollErrorCode.INVALID_OPTION),
            Arguments.of(hidden, 1, PollErrorCode.POLL_NOT_FOUND)
        );
    }

    @Test
    void 저장에서_유니크_위반이_나면_cast도_ALREADY_VOTED다() {
        var poll = VoteFixture.withIds(VoteFixture.openPoll(1L));
        given(voteRepository.saveAndFlush(any(Vote.class))).willThrow(violation(ConstraintKind.UNIQUE));

        assertThatThrownBy(() -> voteWriter.cast(poll, Voter.member(7L), 1, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());
    }

    // ---- saveOnce: 저장 한 번의 변환 ----

    @Test
    void 저장에_성공하면_saveAndFlush를_부르고_예외가_없다() {
        assertThatCode(() -> voteWriter.saveOnce(vote)).doesNotThrowAnyException();

        verify(voteRepository).saveAndFlush(vote);
    }

    @Test
    void 유니크_제약_위반은_원인을_이어_ALREADY_VOTED로_바꿔_던진다() {
        var original = violation(ConstraintKind.UNIQUE);
        given(voteRepository.saveAndFlush(vote)).willThrow(original);

        assertThatThrownBy(() -> voteWriter.saveOnce(vote))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage())
            .hasCause(original);
    }

    @ParameterizedTest
    @MethodSource("그대로_올리는_예외")
    void 유니크_위반이_아닌_예외는_바꾸지_않고_그대로_올린다(RuntimeException original) {
        given(voteRepository.saveAndFlush(vote)).willThrow(original);

        assertThatThrownBy(() -> voteWriter.saveOnce(vote)).isSameAs(original);
        assertThat(original).isNotInstanceOf(AuddumException.class);
    }

    static Stream<RuntimeException> 그대로_올리는_예외() {
        return Stream.of(
            violation(ConstraintKind.FOREIGN_KEY),
            violation(ConstraintKind.CHECK),
            violation(ConstraintKind.OTHER),
            new DataIntegrityViolationException("원인 없음"),
            new QueryTimeoutException("시간 초과")
        );
    }

    private static DataIntegrityViolationException violation(ConstraintKind kind) {
        var cause = new ConstraintViolationException("제약 위반", new SQLException("test"), kind, "uk_vote_test");

        return new DataIntegrityViolationException("저장 실패", cause);
    }

}
