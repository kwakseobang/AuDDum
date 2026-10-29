package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.NOW;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 실제 DB(H2)의 유니크 제약 위반이 409 ALREADY_VOTED가 되는지 확인한다(동시 요청, 트랜잭션 경계).
 * 이긴 쪽 기록이 이미 커밋된 상태에서 진 쪽이 같은 참여자로 저장하면 사전 조회와 상관없이 저장 시점에
 * 제약이 걸린다. 분류 규칙(유니크만 변환, 그 밖의 DB 오류는 그대로)은 VoteWriterTest가 목 repository로 검증한다.
 * 이 클래스는 @Transactional을 붙이지 않는다. 테스트가 트랜잭션을 열어 두면 이미 커밋된 이긴 쪽 기록과 진 쪽의 INSERT 충돌을
 * 저장 한 번의 트랜잭션 경계 그대로 재현할 수 없다. 데이터는 실제로 커밋되므로 @AfterEach에서 지운다.
 * 스레드 경합 자체는 실제 MySQL이 필요해 다루지 않는다.
 */
@RequiredArgsConstructor
class VoteWriterConflictTest extends VoteIntegrationTestSupport {

    final VoteWriter voteWriter;
    final AnonCookieProvider anonCookieProvider;

    @AfterEach
    void cleanUp() {
        voteJpaRepository.deleteAll();
        pollJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    @Test
    void 이긴_쪽이_커밋한_회원_키로_다른_선택을_저장하면_ALREADY_VOTED이고_이긴_쪽_기록만_남는다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        voteWriter.cast(poll, Voter.member(memberId), 2, NOW);

        assertThatThrownBy(() -> voteWriter.cast(poll, Voter.member(memberId), 1, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage())
            .hasCauseInstanceOf(DataIntegrityViolationException.class);

        var remaining = voteJpaRepository.findAll();
        assertThat(remaining).hasSize(1);
        var reloaded = pollJpaRepository.findByPublicId(poll.getPublicId().value()).orElseThrow();
        assertThat(remaining.get(0).getOptionId()).isEqualTo(reloaded.getOptions().get(1).getId());
    }

    @Test
    void 이긴_쪽이_커밋한_비회원_키로_같은_선택을_저장해도_ALREADY_VOTED이고_기록은_한_건이다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var voter = Voter.anonymous(new AnonId(anonCookieProvider.issue().id()));
        voteWriter.cast(poll, voter, 1, NOW);

        assertThatThrownBy(() -> voteWriter.cast(poll, voter, 1, NOW))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());

        assertThat(voteJpaRepository.count()).isEqualTo(1);
    }

}
