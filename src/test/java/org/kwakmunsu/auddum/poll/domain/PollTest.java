package org.kwakmunsu.auddum.poll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.poll.domain.PollFixture.AUTHOR_ID;
import static org.kwakmunsu.auddum.poll.domain.PollFixture.NOW;
import static org.kwakmunsu.auddum.poll.domain.PollFixture.OTHER_MEMBER_ID;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.test.util.ReflectionTestUtils;

class PollTest {

    // ---- 생성: 질문·카테고리 ----

    @Test
    void 투표를_만들면_공개_상태다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThat(poll.getStatus()).isEqualTo(PollStatus.PUBLIC);
        assertThat(poll.isPublic()).isTrue();
    }

    @Test
    void 투표를_만들면_12자_공개_ID가_부여된다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThat(poll.getPublicId().value()).matches("[A-Za-z0-9]{12}");
    }

    @Test
    void 투표를_만들면_질문이_그대로_담긴다() {
        assertThat(PollFixture.pollWithoutCloses().getQuestion().value()).isEqualTo("점심 뭐 먹지?");
    }

    @Test
    void 오백_자_질문으로_투표를_만들_수_있다() {
        assertThatCode(() -> Poll.create(AUTHOR_ID, PollFixture.repeatChar(500), Category.ETC, List.of("a", "b"), null, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void 오백한_자_질문으로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, PollFixture.repeatChar(501), Category.ETC, List.of("a", "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문은 1~500자여야 합니다.");
    }

    @Test
    void 줄바꿈이_있는_질문으로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "가\n나", Category.ETC, List.of("a", "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문에는 줄바꿈을 넣을 수 없습니다.");
    }

    @Test
    void 카테고리_없이_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", null, List.of("a", "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("카테고리는 필수입니다.");
    }

    // ---- 생성: 선택지 개수·길이 ----

    @Test
    void 선택지_한_개로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("a"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 2~5개여야 합니다.");
    }

    @Test
    void 선택지_두_개로_투표를_만들_수_있다() {
        var poll = Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("a", "b"), null, NOW);

        assertThat(poll.getOptions()).hasSize(2);
    }

    @Test
    void 선택지_다섯_개로_투표를_만들_수_있다() {
        var poll = Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("a", "b", "c", "d", "e"), null, NOW);

        assertThat(poll.getOptions()).hasSize(5);
    }

    @Test
    void 선택지_여섯_개로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("a", "b", "c", "d", "e", "f"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 2~5개여야 합니다.");
    }

    @Test
    void 선택지가_없으면_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of(), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 2~5개여야 합니다.");
    }

    @Test
    void 오십_자_선택지로_투표를_만들_수_있다() {
        assertThatCode(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of(PollFixture.repeatChar(50), "b"), null, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void 오십한_자_선택지로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of(PollFixture.repeatChar(51), "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 1~50자여야 합니다.");
    }

    @Test
    void 빈_선택지로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("", "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 1~50자여야 합니다.");
    }

    @Test
    void 줄바꿈이_있는_선택지로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("a\nb", "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지에는 줄바꿈을 넣을 수 없습니다.");
    }

    @Test
    void 선택지는_입력_순서대로_1부터_순번을_받는다() {
        var poll = Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("가", "나", "다"), null, NOW);

        assertThat(poll.getOptions()).extracting(o -> o.getPosition().value()).containsExactly(1, 2, 3);
        assertThat(poll.getOptions()).extracting(o -> o.getContent().value()).containsExactly("가", "나", "다");
    }

    // ---- 생성: 선택지 중복 ----

    @Test
    void 같은_선택지가_두_번_있으면_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("국밥", "국밥"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 서로 달라야 합니다.");
    }

    @Test
    void 대소문자만_다른_선택지는_중복이다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("Pasta", "pASTA"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 서로 달라야 합니다.");
    }

    @Test
    void 악센트만_다른_선택지는_중복이다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("café", "cafe"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 서로 달라야 합니다.");
    }

    @Test
    void 대소문자와_악센트가_모두_다른_선택지도_중복이다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("CAFÉ", "cafe"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 서로 달라야 합니다.");
    }

    @Test
    void 앞뒤_공백을_제거하면_같은_선택지는_중복이다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("국밥", "  국밥 "), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 서로 달라야 합니다.");
    }

    @Test
    void 중간_공백이_다른_선택지는_중복이_아니다() {
        assertThatCode(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("a b", "ab"), null, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void 다섯_개_중_뒤쪽_두_개가_중복이어도_거부한다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("a", "b", "c", "d", "D"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 서로 달라야 합니다.");
    }

    // ---- 생성: 마감 시각 ----

    @Test
    void 마감_시각_없이_투표를_만들_수_있다() {
        assertThatCode(PollFixture::pollWithoutCloses).doesNotThrowAnyException();
    }

    @Test
    void 미래_마감_시각으로_투표를_만들_수_있다() {
        var poll = PollFixture.pollWithCloses(NOW.plusNanos(1));

        assertThat(poll.getClosesAt().value()).isEqualTo(NOW.plusNanos(1));
    }

    @Test
    void 현재와_같은_마감_시각으로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> PollFixture.pollWithCloses(NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("마감 시각은 현재보다 뒤여야 합니다.");
    }

    @Test
    void 과거_마감_시각으로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> PollFixture.pollWithCloses(NOW.minusNanos(1)))
                .isInstanceOf(AuddumException.class)
                .hasMessage("마감 시각은 현재보다 뒤여야 합니다.");
    }

    // ---- 마감 판단 ----

    @Test
    void 마감_시각_직전에는_마감되지_않았다() {
        var closesAt = NOW.plusHours(1);
        var poll = PollFixture.pollWithCloses(closesAt);

        assertThat(poll.isClosed(closesAt.minusNanos(1))).isFalse();
    }

    @Test
    void 마감_시각과_정확히_같으면_마감이다() {
        var closesAt = NOW.plusHours(1);
        var poll = PollFixture.pollWithCloses(closesAt);

        assertThat(poll.isClosed(closesAt)).isTrue();
    }

    @Test
    void 마감_시각_직후에는_마감이다() {
        var closesAt = NOW.plusHours(1);
        var poll = PollFixture.pollWithCloses(closesAt);

        assertThat(poll.isClosed(closesAt.plusNanos(1))).isTrue();
    }

    @Test
    void 마감_시각이_없으면_언제든_마감되지_않는다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThat(poll.isClosed(NOW.plusYears(100))).isFalse();
    }

    // ---- 참여 가능 보장 ----

    @Test
    void 공개이고_마감_전이면_참여할_수_있다() {
        var poll = PollFixture.pollWithCloses(NOW.plusHours(1));

        assertThatCode(() -> poll.ensureVotable(NOW.plusHours(1).minusNanos(1))).doesNotThrowAnyException();
    }

    @Test
    void 마감_시각과_정확히_같은_시각에는_참여할_수_없다() {
        var poll = PollFixture.pollWithCloses(NOW.plusHours(1));

        assertThatThrownBy(() -> poll.ensureVotable(NOW.plusHours(1)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_CLOSED.getMessage());
    }

    @Test
    void 마감된_투표에는_참여할_수_없다() {
        var poll = PollFixture.pollWithCloses(NOW.plusHours(1));

        assertThatThrownBy(() -> poll.ensureVotable(NOW.plusHours(2)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_CLOSED.getMessage());
    }

    @Test
    void 숨김_투표에는_참여할_수_없다() {
        var poll = PollFixture.hiddenPoll();

        assertThatThrownBy(() -> poll.ensureVotable(NOW.plusMinutes(2)))
                .isInstanceOf(AuddumException.class);
    }

    @Test
    void 삭제된_투표에는_참여할_수_없다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.ensureVotable(NOW.plusMinutes(2)))
                .isInstanceOf(AuddumException.class);
    }

    // ---- 선택지 소속 ----

    @Test
    void 투표의_선택지_순번이면_선택지를_돌려준다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThat(poll.ensureHasOption(2).getContent().value()).isEqualTo("파스타");
    }

    @Test
    void 순번_0은_이_투표의_선택지가_아니다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.ensureHasOption(0))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.INVALID_OPTION.getMessage());
    }

    @Test
    void 선택지_개수를_넘는_순번은_이_투표의_선택지가_아니다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.ensureHasOption(3))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.INVALID_OPTION.getMessage());
    }

    @Test
    void 범위_밖_순번_6은_이_투표의_선택지가_아니다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.ensureHasOption(6))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.INVALID_OPTION.getMessage());
    }

    @Test
    void 음수_순번은_이_투표의_선택지가_아니다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.ensureHasOption(-1))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.INVALID_OPTION.getMessage());
    }

    // ---- 작성자 ----

    @Test
    void 작성자_ID면_작성자다() {
        assertThat(PollFixture.pollWithoutCloses().isAuthor(AUTHOR_ID)).isTrue();
    }

    @Test
    void 다른_회원은_작성자가_아니다() {
        assertThat(PollFixture.pollWithoutCloses().isAuthor(OTHER_MEMBER_ID)).isFalse();
    }

    // ---- 상태 전이: 허용 ----

    @Test
    void 관리자는_공개_투표를_숨길_수_있다() {
        var poll = PollFixture.pollWithoutCloses();

        poll.hide();

        assertThat(poll.getStatus()).isEqualTo(PollStatus.HIDDEN);
        assertThat(poll.isPublic()).isFalse();
    }

    @Test
    void 관리자는_숨김_투표를_복구할_수_있다() {
        var poll = PollFixture.hiddenPoll();

        poll.restore();

        assertThat(poll.getStatus()).isEqualTo(PollStatus.PUBLIC);
    }

    @Test
    void 작성자는_공개_투표를_삭제할_수_있다() {
        var poll = PollFixture.pollWithoutCloses();

        poll.deleteByAuthor(AUTHOR_ID);

        assertThat(poll.getStatus()).isEqualTo(PollStatus.DELETED);
    }

    @Test
    void 관리자는_공개_투표를_삭제할_수_있다() {
        var poll = PollFixture.pollWithoutCloses();

        poll.deleteByAdmin();

        assertThat(poll.getStatus()).isEqualTo(PollStatus.DELETED);
    }

    @Test
    void 관리자는_숨김_투표를_삭제할_수_있다() {
        var poll = PollFixture.hiddenPoll();

        poll.deleteByAdmin();

        assertThat(poll.getStatus()).isEqualTo(PollStatus.DELETED);
    }

    @Test
    void 복구한_투표는_다시_숨길_수_있다() {
        var poll = PollFixture.hiddenPoll();
        poll.restore();

        poll.hide();

        assertThat(poll.getStatus()).isEqualTo(PollStatus.HIDDEN);
    }

    @Test
    void 복구한_투표에는_다시_참여할_수_있다() {
        var poll = PollFixture.hiddenPoll();
        poll.restore();

        assertThatCode(() -> poll.ensureVotable(NOW.plusMinutes(3))).doesNotThrowAnyException();
    }

    // ---- 상태 전이: 금지 ----

    @Test
    void 관리자가_숨긴_투표는_작성자가_삭제할_수_없다() {
        var poll = PollFixture.hiddenPoll();

        assertThatThrownBy(() -> poll.deleteByAuthor(AUTHOR_ID))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_DELETABLE.getMessage());
    }

    @Test
    void 작성자_삭제가_거부되면_숨김_상태가_유지된다() {
        var poll = PollFixture.hiddenPoll();

        assertThatThrownBy(() -> poll.deleteByAuthor(AUTHOR_ID))
                .isInstanceOf(AuddumException.class);

        assertThat(poll.getStatus()).isEqualTo(PollStatus.HIDDEN);
    }

    @Test
    void 작성자가_아닌_회원은_투표를_삭제할_수_없다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.deleteByAuthor(OTHER_MEMBER_ID))
                .isInstanceOf(AuddumException.class)
                .hasMessage(DefaultErrorCode.FORBIDDEN.getMessage());
    }

    @Test
    void 작성자가_아닌_회원이_숨김_투표를_삭제하려_하면_존재를_숨기려고_404다() {
        var poll = PollFixture.hiddenPoll();

        assertThatThrownBy(() -> poll.deleteByAuthor(OTHER_MEMBER_ID))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 작성자가_아닌_회원이_삭제된_투표를_삭제하려_하면_404다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.deleteByAuthor(OTHER_MEMBER_ID))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 작성자가_아닌_회원의_삭제가_거부되면_공개_상태가_유지된다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.deleteByAuthor(OTHER_MEMBER_ID))
                .isInstanceOf(AuddumException.class);

        assertThat(poll.getStatus()).isEqualTo(PollStatus.PUBLIC);
    }

    @Test
    void 공개_투표는_복구할_수_없다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.restore())
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_HIDDEN.getMessage());
    }

    @Test
    void 삭제된_투표는_복구할_수_없다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.restore())
                .isInstanceOf(AuddumException.class);
        assertThat(poll.getStatus()).isEqualTo(PollStatus.DELETED);
    }

    @Test
    void 삭제된_투표는_숨길_수_없다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.hide())
                .isInstanceOf(AuddumException.class);
        assertThat(poll.getStatus()).isEqualTo(PollStatus.DELETED);
    }

    @Test
    void 삭제된_투표는_작성자가_다시_삭제할_수_없다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.deleteByAuthor(AUTHOR_ID))
                .isInstanceOf(AuddumException.class);
        assertThat(poll.getStatus()).isEqualTo(PollStatus.DELETED);
    }

    @Test
    void 삭제된_투표는_관리자가_다시_삭제할_수_없다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.deleteByAdmin())
                .isInstanceOf(AuddumException.class);
        assertThat(poll.getStatus()).isEqualTo(PollStatus.DELETED);
    }

    // ---- 공백 제거 (DR-1) ----

    @Test
    void 질문의_앞뒤_공백은_제거한_값이_저장된다() {
        var poll = Poll.create(AUTHOR_ID, "  점심?  ", Category.ETC, List.of("a", "b"), null, NOW);

        assertThat(poll.getQuestion().value()).isEqualTo("점심?");
    }

    @Test
    void 공백만_있는_질문으로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "   ", Category.ETC, List.of("a", "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문은 1~500자여야 합니다.");
    }

    @Test
    void 앞뒤_공백이_붙은_오백_자_질문으로_투표를_만들_수_있다() {
        assertThatCode(() -> Poll.create(AUTHOR_ID, " " + PollFixture.repeatChar(500) + " ", Category.ETC, List.of("a", "b"), null, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void 선택지의_앞뒤_공백은_제거한_값이_저장된다() {
        var poll = Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("  국밥 ", "파스타"), null, NOW);

        assertThat(poll.getOptions()).extracting(o -> o.getContent().value()).containsExactly("국밥", "파스타");
    }

    @Test
    void 공백만_있는_선택지로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of("   ", "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 1~50자여야 합니다.");
    }

    @Test
    void 앞뒤_공백이_붙은_오십_자_선택지로_투표를_만들_수_있다() {
        assertThatCode(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of(" " + PollFixture.repeatChar(50) + " ", "b"), null, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void 앞뒤_공백이_붙은_오십한_자_선택지로_투표를_만들_수_없다() {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of(" " + PollFixture.repeatChar(51) + " ", "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지는 1~50자여야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가\u000B나", "가\u000C나", "가\u0085나", "가\u2028나", "가\u2029나"})
    void 유니코드_줄_구분자가_있는_질문으로_투표를_만들_수_없다(String question) {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, question, Category.ETC, List.of("a", "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("질문에는 줄바꿈을 넣을 수 없습니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가\u000B나", "가\u000C나", "가\u0085나", "가\u2028나", "가\u2029나"})
    void 유니코드_줄_구분자가_있는_선택지로_투표를_만들_수_없다(String option) {
        assertThatThrownBy(() -> Poll.create(AUTHOR_ID, "질문", Category.ETC, List.of(option, "b"), null, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage("선택지에는 줄바꿈을 넣을 수 없습니다.");
    }

    // ---- 이미 숨김 투표 hide (DR-1) ----

    @Test
    void 이미_숨겨진_투표를_다시_숨겨도_예외가_없다() {
        var poll = PollFixture.hiddenPoll();

        assertThatCode(() -> poll.hide()).doesNotThrowAnyException();
        assertThat(poll.getStatus()).isEqualTo(PollStatus.HIDDEN);
    }

    // ---- 오류 코드 강화 (DR-3) ----

    @Test
    void 숨김_투표_참여는_POLL_NOT_FOUND다() {
        var poll = PollFixture.hiddenPoll();

        assertThatThrownBy(() -> poll.ensureVotable(NOW.plusMinutes(2)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 삭제된_투표_참여는_POLL_NOT_FOUND다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.ensureVotable(NOW.plusMinutes(2)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 삭제된_투표_복구는_POLL_NOT_HIDDEN이다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.restore())
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_HIDDEN.getMessage());
    }

    @Test
    void 삭제된_투표_숨김은_POLL_NOT_FOUND다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.hide())
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 삭제된_투표_작성자_삭제는_POLL_NOT_FOUND다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.deleteByAuthor(AUTHOR_ID))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 삭제된_투표_관리자_삭제는_POLL_NOT_FOUND다() {
        var poll = PollFixture.deletedPoll();

        assertThatThrownBy(() -> poll.deleteByAdmin())
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    // ---- 직전 투표 뒤 생성 ----

    @Test
    void 질문이_같으면_직전_투표_뒤에_만들_수_없다() {
        var latest = pollCreatedAt("점심 뭐 먹지?", NOW.minusDays(30));
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.ensureCreatableAfter(latest, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.DUPLICATE_POLL.getMessage());
    }

    @Test
    void 앞뒤_공백만_다른_같은_질문도_직전_투표_뒤에_만들_수_없다() {
        var latest = pollCreatedAt("  점심 뭐 먹지?  ", NOW.minusDays(30));
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.ensureCreatableAfter(latest, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.DUPLICATE_POLL.getMessage());
    }

    @Test
    void 대소문자만_다른_질문은_같은_질문이_아니다() {
        var latest = pollCreatedAt("Hello there", NOW.minusDays(30));
        var poll = Poll.create(AUTHOR_ID, "hello there", Category.ETC, List.of("가", "나"), null, NOW);

        assertThatCode(() -> poll.ensureCreatableAfter(latest, NOW)).doesNotThrowAnyException();
    }

    @Test
    void 직전_생성_후_30초가_지나지_않았으면_다른_질문도_만들_수_없다() {
        var latest = pollCreatedAt("다른 질문", NOW.minusSeconds(29));
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.ensureCreatableAfter(latest, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.POLL_RATE_LIMITED.getMessage());
    }

    @Test
    void 직전_생성_후_정확히_30초가_지나면_만들_수_있다() {
        var latest = pollCreatedAt("다른 질문", NOW.minusSeconds(30));
        var poll = PollFixture.pollWithoutCloses();

        assertThatCode(() -> poll.ensureCreatableAfter(latest, NOW)).doesNotThrowAnyException();
    }

    @Test
    void 질문이_같고_간격도_짧으면_DUPLICATE_POLL이_우선한다() {
        var latest = pollCreatedAt("점심 뭐 먹지?", NOW.minusSeconds(5));
        var poll = PollFixture.pollWithoutCloses();

        assertThatThrownBy(() -> poll.ensureCreatableAfter(latest, NOW))
                .isInstanceOf(AuddumException.class)
                .hasMessage(PollErrorCode.DUPLICATE_POLL.getMessage());
    }

    @Test
    void 공개_투표는_누구나_볼_수_있고_관리자_여부는_확인하지_않는다() {
        var poll = PollFixture.pollWithoutCloses();
        var adminChecks = new AtomicInteger();

        var readable = poll.isReadableBy(null, () -> countAndReturn(adminChecks, false));

        assertThat(readable).isTrue();
        assertThat(adminChecks.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 숨김_투표는_작성자가_볼_수_있고_관리자_여부는_확인하지_않는다(boolean hidden) {
        var poll = hidden ? PollFixture.hiddenPoll() : PollFixture.deletedPoll();
        var adminChecks = new AtomicInteger();

        var readable = poll.isReadableBy(AUTHOR_ID, () -> countAndReturn(adminChecks, false));

        assertThat(readable).isTrue();
        assertThat(adminChecks.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 숨김_삭제_투표는_작성자가_아니어도_관리자면_볼_수_있다(boolean hidden) {
        var poll = hidden ? PollFixture.hiddenPoll() : PollFixture.deletedPoll();

        assertThat(poll.isReadableBy(OTHER_MEMBER_ID, () -> true)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 숨김_삭제_투표는_작성자도_관리자도_아니면_볼_수_없다(boolean hidden) {
        var poll = hidden ? PollFixture.hiddenPoll() : PollFixture.deletedPoll();

        assertThat(poll.isReadableBy(OTHER_MEMBER_ID, () -> false)).isFalse();
        assertThat(poll.isReadableBy(null, () -> false)).isFalse();
    }

    // ---- 결과 공개 판단 (API-명세 §4 결과 공개 규칙, 예외-정책 P-03·P-25) ----

    @Test
    void 마감_전에_참여하지_않은_사람에게는_결과를_공개하지_않는다() {
        var poll = PollFixture.pollWithCloses(NOW.plusHours(1));

        assertThat(poll.isResultOpen(false, NOW)).isFalse();
    }

    @Test
    void 마감_전이어도_참여한_사람에게는_결과를_공개한다() {
        var poll = PollFixture.pollWithCloses(NOW.plusHours(1));

        assertThat(poll.isResultOpen(true, NOW)).isTrue();
    }

    @Test
    void 마감_후에는_참여하지_않은_사람에게도_결과를_공개한다() {
        var poll = PollFixture.pollWithCloses(NOW.plusHours(1));

        assertThat(poll.isResultOpen(false, NOW.plusHours(2))).isTrue();
    }

    @Test
    void 마감_후에도_참여한_사람에게는_결과를_공개한다() {
        var poll = PollFixture.pollWithCloses(NOW.plusHours(1));

        assertThat(poll.isResultOpen(true, NOW.plusHours(2))).isTrue();
    }

    @Test
    void 마감_시각_직전에는_미참여자에게_결과를_공개하지_않는다() {
        var closesAt = NOW.plusHours(1);
        var poll = PollFixture.pollWithCloses(closesAt);

        assertThat(poll.isResultOpen(false, closesAt.minusNanos(1))).isFalse();
    }

    @Test
    void 마감_시각과_정확히_같으면_미참여자에게도_결과를_공개한다() {
        var closesAt = NOW.plusHours(1);
        var poll = PollFixture.pollWithCloses(closesAt);

        assertThat(poll.isResultOpen(false, closesAt)).isTrue();
    }

    @Test
    void 마감_시각_직후에는_미참여자에게도_결과를_공개한다() {
        var closesAt = NOW.plusHours(1);
        var poll = PollFixture.pollWithCloses(closesAt);

        assertThat(poll.isResultOpen(false, closesAt.plusNanos(1))).isTrue();
    }

    @Test
    void 마감_시각이_없는_투표는_미참여자에게_언제나_결과를_공개하지_않는다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThat(poll.isResultOpen(false, NOW.plusYears(100))).isFalse();
    }

    @Test
    void 마감_시각이_없는_투표도_참여한_사람에게는_결과를_공개한다() {
        var poll = PollFixture.pollWithoutCloses();

        assertThat(poll.isResultOpen(true, NOW)).isTrue();
    }

    // ---- 선택지 id로 순번 찾기 ----

    @Test
    void 선택지_id의_순번을_돌려준다() {
        var poll = pollWithOptionIds();

        assertThat(poll.positionOf(101L)).isEqualTo(1);
        assertThat(poll.positionOf(102L)).isEqualTo(2);
    }

    @Test
    void 이_투표의_선택지가_아닌_id면_IllegalArgumentException이다() {
        var poll = pollWithOptionIds();

        assertThatThrownBy(() -> poll.positionOf(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    private boolean countAndReturn(AtomicInteger counter, boolean value) {
        counter.incrementAndGet();

        return value;
    }

    private Poll pollWithOptionIds() {
        var poll = PollFixture.pollWithoutCloses();
        ReflectionTestUtils.setField(poll.getOptions().get(0), "id", 101L);
        ReflectionTestUtils.setField(poll.getOptions().get(1), "id", 102L);

        return poll;
    }

    private Poll pollCreatedAt(String question, LocalDateTime createdAt) {
        var poll = Poll.create(AUTHOR_ID, question, Category.ETC, List.of("가", "나"), null, NOW);
        ReflectionTestUtils.setField(poll, "createdAt", createdAt);

        return poll;
    }

}
