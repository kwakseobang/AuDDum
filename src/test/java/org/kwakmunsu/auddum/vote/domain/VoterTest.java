package org.kwakmunsu.auddum.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.vote.domain.VoteFixture.anyAnonId;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    @ParameterizedTest
    @MethodSource("정확히_하나가_아닌_조합")
    void 회원_ID와_익명_식별자는_정확히_하나만_줄_수_있다(Long memberId, AnonId anonId) {
        assertThatThrownBy(() -> new Voter(memberId, anonId))
                .isInstanceOf(AuddumException.class)
                .hasMessage("참여자는 회원 ID와 익명 식별자 중 하나여야 합니다.");
    }

    static Stream<Arguments> 정확히_하나가_아닌_조합() {
        return Stream.of(
                Arguments.of(7L, new AnonId(UUID.randomUUID())),   // 둘 다 줌
                Arguments.of(null, null)                            // 둘 다 비움
        );
    }

    @ParameterizedTest
    @MethodSource("같은_참여자")
    void 같은_회원_ID_또는_같은_UUID의_참여자는_같다(Voter left, Voter right) {
        assertThat(left).isEqualTo(right);
    }

    static Stream<Arguments> 같은_참여자() {
        var uuid = UUID.randomUUID();

        return Stream.of(
                Arguments.of(Voter.member(7L), Voter.member(7L)),
                Arguments.of(Voter.anonymous(new AnonId(uuid)), Voter.anonymous(new AnonId(uuid)))
        );
    }

    @ParameterizedTest
    @MethodSource("다른_참여자")
    void 회원_ID나_UUID가_다르거나_회원과_익명이면_참여자는_다르다(Voter left, Voter right) {
        assertThat(left).isNotEqualTo(right);
    }

    static Stream<Arguments> 다른_참여자() {
        return Stream.of(
                Arguments.of(Voter.member(7L), Voter.member(8L)),
                Arguments.of(Voter.anonymous(anyAnonId()), Voter.anonymous(anyAnonId())),
                Arguments.of(Voter.member(7L), Voter.anonymous(anyAnonId()))
        );
    }

}
