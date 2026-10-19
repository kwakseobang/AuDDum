package org.kwakmunsu.auddum.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.vote.domain.VoteFixture.anyAnonId;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.AuddumException;

class VoterTest {

    @Test
    void 회원_ID로_참여자를_만들_수_있다() {
        var voter = Voter.member(7L);

        assertThat(voter.memberId()).isEqualTo(7L);
        assertThat(voter.anonId()).isNull();
    }

    @Test
    void 익명_식별자로_참여자를_만들_수_있다() {
        var anonId = anyAnonId();

        var voter = Voter.anonymous(anonId);

        assertThat(voter.anonId()).isEqualTo(anonId);
        assertThat(voter.memberId()).isNull();
    }

    @Test
    void 회원_ID와_익명_식별자를_모두_주면_만들_수_없다() {
        assertThatThrownBy(() -> new Voter(7L, anyAnonId()))
                .isInstanceOf(AuddumException.class);
    }

    @Test
    void 회원_ID와_익명_식별자를_모두_비우면_만들_수_없다() {
        assertThatThrownBy(() -> new Voter(null, null))
                .isInstanceOf(AuddumException.class);
    }

    @Test
    void 같은_회원_ID의_참여자는_같다() {
        assertThat(Voter.member(7L)).isEqualTo(Voter.member(7L));
    }

    @Test
    void 다른_회원_ID의_참여자는_다르다() {
        assertThat(Voter.member(7L)).isNotEqualTo(Voter.member(8L));
    }

    @Test
    void 같은_UUID의_익명_참여자는_같다() {
        var uuid = UUID.randomUUID();

        assertThat(Voter.anonymous(new AnonId(uuid))).isEqualTo(Voter.anonymous(new AnonId(uuid)));
    }

    @Test
    void 다른_UUID의_익명_참여자는_다르다() {
        assertThat(Voter.anonymous(anyAnonId())).isNotEqualTo(Voter.anonymous(anyAnonId()));
    }

    @Test
    void 회원_참여자와_익명_참여자는_다르다() {
        assertThat(Voter.member(7L)).isNotEqualTo(Voter.anonymous(anyAnonId()));
    }

}
