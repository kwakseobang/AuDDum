package org.kwakmunsu.auddum.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kwakmunsu.auddum.support.error.AuddumException;

class ReporterTest {

    @Test
    void 회원_ID로_신고자를_만들_수_있다() {
        var reporter = Reporter.member(7L);

        assertThat(reporter.memberId()).isEqualTo(7L);
        assertThat(reporter.anonId()).isNull();
    }

    @Test
    void 익명_식별자로_신고자를_만들_수_있다() {
        var anonId = UUID.randomUUID();

        var reporter = Reporter.anonymous(anonId);

        assertThat(reporter.anonId()).isEqualTo(anonId);
        assertThat(reporter.memberId()).isNull();
    }

    @ParameterizedTest
    @MethodSource("정확히_하나가_아닌_조합")
    void 회원_ID와_익명_식별자는_정확히_하나만_줄_수_있다(Long memberId, UUID anonId) {
        assertThatThrownBy(() -> new Reporter(memberId, anonId))
                .isInstanceOf(AuddumException.class)
                .hasMessage("신고자는 회원 ID와 익명 식별자 중 하나여야 합니다.");
    }

    static Stream<Arguments> 정확히_하나가_아닌_조합() {
        return Stream.of(
                Arguments.of(7L, UUID.randomUUID()),   // 둘 다 줌
                Arguments.of(null, null)               // 둘 다 비움
        );
    }

    @ParameterizedTest
    @MethodSource("같은_신고자")
    void 같은_회원_ID_또는_같은_UUID의_신고자는_같다(Reporter left, Reporter right) {
        assertThat(left).isEqualTo(right);
    }

    static Stream<Arguments> 같은_신고자() {
        var uuid = UUID.randomUUID();

        return Stream.of(
                Arguments.of(Reporter.member(7L), Reporter.member(7L)),
                Arguments.of(Reporter.anonymous(uuid), Reporter.anonymous(uuid))
        );
    }

    @ParameterizedTest
    @MethodSource("다른_신고자")
    void 다른_신고자는_같지_않다(Reporter left, Reporter right) {
        assertThat(left).isNotEqualTo(right);
    }

    static Stream<Arguments> 다른_신고자() {
        return Stream.of(
                Arguments.of(Reporter.member(7L), Reporter.member(8L)),
                Arguments.of(Reporter.anonymous(UUID.randomUUID()), Reporter.anonymous(UUID.randomUUID())),
                Arguments.of(Reporter.member(7L), Reporter.anonymous(UUID.randomUUID()))
        );
    }

}
