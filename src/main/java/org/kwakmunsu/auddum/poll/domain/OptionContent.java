package org.kwakmunsu.auddum.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.kwakmunsu.auddum.support.error.AuddumException;

/**
 * 선택지 내용. 앞뒤 공백을 제거하고 1~50자(유니코드 문자 단위), 줄바꿈 없음.
 */
@Embeddable
public record OptionContent(
    @Column(name = "content", nullable = false, length = 50)
    String value
) {

    static final int MAX_LENGTH = 50;
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");

    public OptionContent {
        if (value == null) {
            throw AuddumException.validation("options", "선택지는 필수입니다.");
        }

        value = value.strip();
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > MAX_LENGTH) {
            throw AuddumException.validation("options", "선택지는 1~50자여야 합니다.");
        }
        if (PollQuestion.hasLineBreak(value)) {
            throw AuddumException.validation("options", "선택지에는 줄바꿈을 넣을 수 없습니다.");
        }
    }

    public static OptionContent of(String value) {
        return new OptionContent(value);
    }

    /**
     * 중복 판단에 쓰는 키. 대소문자와 악센트를 구분하지 않는다.
     */
    String comparisonKey() {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);

        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

}
