package org.kwakmunsu.auddum.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.kwakmunsu.auddum.support.error.AuddumException;

/** 투표 질문. 앞뒤 공백을 제거하고 1~500자(유니코드 문자 단위), 줄바꿈 없음. */
@Embeddable
public record PollQuestion(
    @Column(name = "question", nullable = false, length = 500) String value
) {

    static final int MAX_LENGTH = 500;

    public PollQuestion {
        if (value == null) {
            throw AuddumException.validation("question", "질문은 필수입니다.");
        }

        value = value.strip();
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > MAX_LENGTH) {
            throw AuddumException.validation("question", "질문은 1~500자여야 합니다.");
        }
        if (hasLineBreak(value)) {
            throw AuddumException.validation("question", "질문에는 줄바꿈을 넣을 수 없습니다.");
        }
    }

    public static PollQuestion of(String value) {
        return new PollQuestion(value);
    }

    static boolean hasLineBreak(String text) {
        return text.chars().anyMatch(c -> c == '\n' || c == '\r' || c == '\u000B' || c == '\u000C' || c == '\u0085' || c == '\u2028' || c == '\u2029');
    }

}
