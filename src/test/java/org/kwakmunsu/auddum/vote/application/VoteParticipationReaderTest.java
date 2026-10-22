package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.poll.application.dto.Viewer;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider;
import org.springframework.transaction.annotation.Transactional;

/**
 * VoteParticipationReader 통합 테스트(H2). poll이 선언한 ParticipationReader를 vote가 구현한 부분이다.
 * 신원 단서(회원 ID, 익명 쿠키)가 참여자로 이어지는 조합은 PollQueryServiceParticipationTest가, 결과 공개 판단은 poll이 하고 PollTest가 본다.
 * 여기서는 포트의 계약만 본다: 내 선택은 신원으로 찾고 알 수 없으면 비어 있으며, 득표는 항상 내려준다.
 */
@RequiredArgsConstructor
@Transactional
class VoteParticipationReaderTest extends VoteIntegrationTestSupport {

    final VoteParticipationReader participationReader;
    final AnonCookieProvider anonCookieProvider;

    @Test
    void 참여한_회원의_선택과_득표를_돌려준다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, Voter.member(memberId));
        saveVote(poll, 1, Voter.member(saveMember()));

        var participation = participationReader.readFor(poll, new Viewer(memberId, null));

        assertThat(participation.participated()).isTrue();
        assertThat(participation.myChoice()).isEqualTo(2);
        assertThat(participation.tally().options()).extracting("position", "count")
            .containsExactly(tuple(1, 1L), tuple(2, 1L));
    }

    @Test
    void 참여한_비회원은_쿠키로_선택을_찾는다() {
        var cookie = anonCookieProvider.issue();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.anonymous(cookie.anonId()));

        var participation = participationReader.readFor(poll, new Viewer(null, cookie.value()));

        assertThat(participation.myChoice()).isEqualTo(1);
    }

    @Test
    void 참여자를_알_수_없으면_선택은_비어_있어도_득표는_내려준다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, Voter.anonymous(new AnonId(UUID.randomUUID())));

        var participation = participationReader.readFor(poll, new Viewer(null, "not-a-cookie"));

        assertThat(participation.participated()).isFalse();
        assertThat(participation.tally().total()).isEqualTo(1);
    }

    @Test
    void 참여하지_않은_회원의_선택은_비어_있다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));

        var participation = participationReader.readFor(poll, new Viewer(saveMember(), null));

        assertThat(participation.myChoice()).isNull();
        assertThat(participation.tally().total()).isEqualTo(1);
    }

}
