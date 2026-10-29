package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.transaction.annotation.Transactional;

/**
 * VoteReader 통합 테스트(H2). 참여 기록 조회, 내 선택, 결과 집계. 기준은 응답의 myChoice와 result,
 * 결과는 항상 vote 기록에서 센다, 회원·비회원 기록은 연결되지 않는다.
 * 결과 공개 판단의 진리표는 PollTest, 서비스 연결과 신원 조합은 PollQueryServiceParticipationTest가 맡는다.
 */
@RequiredArgsConstructor
@Transactional
class VoteReaderTest extends VoteIntegrationTestSupport {

    final VoteReader voteReader;

    // ---- find ----

    @Test
    void 참여한_회원의_기록을_찾는다() {
        var memberId = saveMember();
        var poll = savePoll();
        saveVote(poll, 2, Voter.member(memberId));

        var found = voteReader.find(poll, Voter.member(memberId));

        assertThat(found).isPresent();
        assertThat(found.get().getOptionId()).isEqualTo(poll.getOptions().get(1).getId());
    }

    @Test
    void 참여하지_않은_회원은_기록이_비어_있다() {
        var poll = savePoll();
        saveVote(poll, 1, Voter.member(saveMember()));

        assertThat(voteReader.find(poll, Voter.member(saveMember()))).isEmpty();
    }

    @Test
    void 참여한_비회원의_기록을_찾는다() {
        var poll = savePoll();
        var voter = Voter.anonymous(new AnonId(UUID.randomUUID()));
        saveVote(poll, 1, voter);

        var found = voteReader.find(poll, voter);

        assertThat(found).isPresent();
        assertThat(found.get().getOptionId()).isEqualTo(poll.getOptions().get(0).getId());
    }

    @Test
    void 참여하지_않은_비회원은_기록이_비어_있다() {
        var poll = savePoll();
        saveVote(poll, 1, Voter.anonymous(new AnonId(UUID.randomUUID())));

        assertThat(voteReader.find(poll, Voter.anonymous(new AnonId(UUID.randomUUID())))).isEmpty();
    }

    @Test
    void 다른_투표의_기록은_찾지_않는다() {
        var memberId = saveMember();
        var poll = savePoll();
        var other = savePoll();
        saveVote(other, 1, Voter.member(memberId));

        assertThat(voteReader.find(poll, Voter.member(memberId))).isEmpty();
    }

    @Test
    void 다른_참여자의_기록은_찾지_않는다() {
        var poll = savePoll();
        var anonymous = Voter.anonymous(new AnonId(UUID.randomUUID()));
        saveVote(poll, 1, anonymous);

        assertThat(voteReader.find(poll, Voter.member(saveMember()))).isEmpty();
    }

    // ---- findChoice ----

    @Test
    void 회원이_고른_선택지_순번을_돌려준다() {
        var memberId = saveMember();
        var poll = savePoll();
        saveVote(poll, 2, Voter.member(memberId));

        assertThat(voteReader.findChoice(poll, Voter.member(memberId))).contains(2);
    }

    @Test
    void 비회원이_고른_선택지_순번을_돌려준다() {
        var poll = savePoll();
        var voter = Voter.anonymous(new AnonId(UUID.randomUUID()));
        saveVote(poll, 1, voter);

        assertThat(voteReader.findChoice(poll, voter)).contains(1);
    }

    @Test
    void 참여하지_않았으면_고른_선택지가_비어_있다() {
        var poll = savePoll();
        saveVote(poll, 1, Voter.member(saveMember()));

        assertThat(voteReader.findChoice(poll, Voter.member(saveMember()))).isEmpty();
    }

    // ---- tally ----

    @Test
    void 득표가_0인_선택지도_포함하고_순번_오름차순이다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember(), VoteFixture.fiveOptions()));
        saveVote(poll, 4, Voter.member(saveMember()));
        saveVote(poll, 2, Voter.member(saveMember()));
        saveVote(poll, 4, Voter.member(saveMember()));

        var tally = voteReader.tally(poll);

        assertThat(tally.options()).extracting("position", "count")
            .containsExactly(tuple(1, 0L), tuple(2, 1L), tuple(3, 0L), tuple(4, 2L), tuple(5, 0L));
    }

    @Test
    void total은_선택지별_득표의_합계다() {
        var poll = savePoll();
        saveVote(poll, 1, Voter.member(saveMember()));
        saveVote(poll, 2, Voter.member(saveMember()));
        saveVote(poll, 2, Voter.anonymous(new AnonId(UUID.randomUUID())));

        assertThat(voteReader.tally(poll).total()).isEqualTo(3);
    }

    @Test
    void 다른_투표의_득표는_집계에_영향이_없다() {
        var poll = savePoll();
        var other = savePoll();
        saveVote(poll, 1, Voter.member(saveMember()));
        saveVote(other, 1, Voter.member(saveMember()));
        saveVote(other, 2, Voter.member(saveMember()));

        var tally = voteReader.tally(poll);

        assertThat(tally.total()).isEqualTo(1);
        assertThat(tally.options()).extracting("position", "count").containsExactly(tuple(1, 1L), tuple(2, 0L));
    }

    @Test
    void 투표_기록이_없는_투표는_total이_0이고_모든_선택지가_0이다() {
        var poll = savePoll();

        var tally = voteReader.tally(poll);

        assertThat(tally.total()).isZero();
        assertThat(tally.options()).extracting("position", "count").containsExactly(tuple(1, 0L), tuple(2, 0L));
    }

    // ---- 보조 ----

    private Poll savePoll() {
        return savePoll(VoteFixture.openPoll(saveMember()));
    }

}
