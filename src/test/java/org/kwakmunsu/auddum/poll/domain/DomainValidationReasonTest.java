package org.kwakmunsu.auddum.poll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.poll.domain.PollFixture.NOW;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.FieldViolation;

/**
 * 도메인 검증 실패가 어느 필드가 왜 틀렸는지(errors)까지 담는지 확인한다. 응답의 {@code errors}가 된다.
 */
class DomainValidationReasonTest {

    @Test
    void 질문이_501자면_question_필드와_길이_사유를_담는다() {
        assertViolation(() -> PollQuestion.of("가".repeat(501)), "question", "질문은 1~500자여야 합니다.");
    }

    @Test
    void 질문에_줄바꿈이_있으면_줄바꿈_사유를_담는다() {
        assertViolation(() -> PollQuestion.of("가\n나"), "question", "질문에는 줄바꿈을 넣을 수 없습니다.");
    }

    @Test
    void 선택지가_51자면_options_필드와_길이_사유를_담는다() {
        assertViolation(() -> OptionContent.of("가".repeat(51)), "options", "선택지는 1~50자여야 합니다.");
    }

    @Test
    void 선택지가_1개면_개수_사유를_담는다() {
        assertViolation(() -> Poll.create(1L, "질문", Category.ETC, List.of("하나"), null, NOW),
            "options", "선택지는 2~5개여야 합니다.");
    }

    @Test
    void 선택지가_중복이면_중복_사유를_담는다() {
        assertViolation(() -> Poll.create(1L, "질문", Category.ETC, List.of("Cafe", "café"), null, NOW),
            "options", "선택지는 서로 달라야 합니다.");
    }

    @Test
    void 카테고리가_없으면_category_필드와_필수_사유를_담는다() {
        assertViolation(() -> Poll.create(1L, "질문", (Category) null, List.of("가", "나"), null, NOW),
            "category", "카테고리는 필수입니다.");
    }

    @Test
    void 마감_시각이_현재와_같으면_closesAt_필드와_사유를_담는다() {
        assertViolation(() -> ClosesAt.of(NOW, NOW), "closesAt", "마감 시각은 현재보다 뒤여야 합니다.");
    }

    @Test
    void 선택지_순서가_6이면_position_필드와_사유를_담는다() {
        assertViolation(() -> Position.of(6), "position", "선택지 순서는 1~5여야 합니다.");
    }

    @Test
    void 공개_ID가_11자면_publicId_필드와_사유를_담는다() {
        assertViolation(() -> PublicId.of("abcdefghijk"), "publicId", "공개 ID는 12자 영숫자여야 합니다.");
    }

    private void assertViolation(Runnable action, String field, String reason) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(AuddumException.class, e -> {
                assertThat(e).hasMessage(reason);
                assertThat(e.getErrors()).containsExactly(new FieldViolation(field, reason));
            });
    }

}
