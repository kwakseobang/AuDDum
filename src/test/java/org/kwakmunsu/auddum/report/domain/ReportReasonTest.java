package org.kwakmunsu.auddum.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.support.error.FieldViolation;

/**
 * 신고 사유 값 객체. 기준은 도메인-모델 §6 신고 사유, §8 ReportReason, API-명세 §5.
 * 필드별 사유 문구는 docs에 없어 예외 타입만 확인한다.
 */
class ReportReasonTest {

    private static final int DETAIL_MAX = 200;

    @Test
    void 신고_사유는_허용된_다섯_가지뿐이다() {
        assertThat(ReportReasonCode.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("ABUSE_HATE", "SEXUAL_HARASSMENT", "PERSONAL_INFO", "SPAM", "ETC");
    }

    @ParameterizedTest
    @EnumSource(value = ReportReasonCode.class, names = "ETC", mode = EnumSource.Mode.EXCLUDE)
    void ETC가_아닌_사유는_상세_없이_만들_수_있다(ReportReasonCode code) {
        var reason = new ReportReason(code, null);

        assertThat(reason.code()).isEqualTo(code);
    }

    @ParameterizedTest
    @EnumSource(value = ReportReasonCode.class, names = "ETC", mode = EnumSource.Mode.EXCLUDE)
    void ETC가_아닌_사유에_담긴_상세는_예외_없이_입력_그대로_가진다(ReportReasonCode code) {
        var reason = new ReportReason(code, "담겨 온 상세");

        assertThat(reason.detail()).isEqualTo("담겨 온 상세");
    }

    @Test
    void ETC는_상세가_있으면_만들_수_있다() {
        var reason = new ReportReason(ReportReasonCode.ETC, "특정인을 지목하는 내용입니다.");

        assertThat(reason.code()).isEqualTo(ReportReasonCode.ETC);
        assertThat(reason.detail()).isEqualTo("특정인을 지목하는 내용입니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void ETC인데_상세가_없으면_만들_수_없다(String detail) {
        assertThatThrownBy(() -> new ReportReason(ReportReasonCode.ETC, detail))
                .isInstanceOf(AuddumException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   ", "\t", "\n", " \t\n "})
    void ETC인데_상세가_공백뿐이면_없는_것으로_보아_만들_수_없다(String detail) {
        assertThatThrownBy(() -> new ReportReason(ReportReasonCode.ETC, detail))
                .isInstanceOf(AuddumException.class);
    }

    @Test
    void ETC_상세가_한_글자면_만들_수_있다() {
        assertThatCode(() -> new ReportReason(ReportReasonCode.ETC, "가"))
                .doesNotThrowAnyException();
    }

    @Test
    void ETC_상세가_상한_200자면_만들_수_있다() {
        assertThatCode(() -> new ReportReason(ReportReasonCode.ETC, "가".repeat(DETAIL_MAX)))
                .doesNotThrowAnyException();
    }

    @Test
    void ETC_상세가_상한을_넘어_201자면_만들_수_없다() {
        assertThatThrownBy(() -> new ReportReason(ReportReasonCode.ETC, "가".repeat(DETAIL_MAX + 1)))
                .isInstanceOf(AuddumException.class);
    }

    @Test
    void ETC가_아닌_사유도_상세가_201자면_detail_필드_검증_오류로_만들_수_없다() {
        assertThatThrownBy(() -> new ReportReason(ReportReasonCode.SPAM, "가".repeat(DETAIL_MAX + 1)))
                .isInstanceOfSatisfying(AuddumException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(DefaultErrorCode.VALIDATION_FAILED);
                    assertThat(e.getErrors()).extracting(FieldViolation::field).containsExactly("detail");
                });
    }

    @Test
    void 보조_평면_문자_이모지_200개는_만들_수_있다() {
        assertThatCode(() -> new ReportReason(ReportReasonCode.ETC, "😀".repeat(DETAIL_MAX)))
                .doesNotThrowAnyException();
    }

    @Test
    void 보조_평면_문자_이모지_201개는_만들_수_없다() {
        assertThatThrownBy(() -> new ReportReason(ReportReasonCode.ETC, "😀".repeat(DETAIL_MAX + 1)))
                .isInstanceOf(AuddumException.class);
    }

    @Test
    void 사유_코드가_없으면_만들_수_없다() {
        assertThatThrownBy(() -> new ReportReason(null, null))
                .isInstanceOf(AuddumException.class);
    }

}
