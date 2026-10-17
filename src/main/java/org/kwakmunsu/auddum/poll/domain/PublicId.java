package org.kwakmunsu.auddum.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.security.SecureRandom;
import org.kwakmunsu.auddum.support.error.AuddumException;

/** 외부에 노출하는 투표 식별자. 12자 영숫자다. */
@Embeddable
public record PublicId(
    @Column(name = "public_id", nullable = false, length = 36)
    String value
) {

    static final int LENGTH = 12;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public PublicId {
        if (!isValid(value)) {
            throw AuddumException.validation("publicId", "공개 ID는 12자 영숫자여야 합니다.");
        }
    }

    /** 12자 영숫자인지 확인한다. 형식 오류를 400(MALFORMED_REQUEST)으로 구분해야 하는 조회 쪽이 쓴다. */
    public static boolean isValid(String value) {
        return value != null && value.length() == LENGTH && value.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }

    public static PublicId of(String value) {
        return new PublicId(value);
    }

    /** 새 공개 ID를 발급한다. 중복 확인과 재발급은 호출하는 쪽(저장 시점)이 한다. */
    public static PublicId generate() {
        StringBuilder builder = new StringBuilder(LENGTH);

        for (int i = 0; i < LENGTH; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }

        return new PublicId(builder.toString());
    }

}
