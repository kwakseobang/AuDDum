package org.kwakmunsu.auddum.report.application.dto;

import lombok.AccessLevel;
import lombok.Builder;

/**
 * 신고 접수 결과. 응답 본문은 없고, 쿠키를 새로 발급했을 때만 내려 줄 값이 있다.
 *
 * @param newCookieValue 새로 발급한 익명 쿠키 값. 발급하지 않았으면 null
 */
@Builder(access = AccessLevel.PRIVATE)
public record SubmitResult(String newCookieValue) {

    public static SubmitResult of(String newCookieValue) {
        return SubmitResult.builder()
            .newCookieValue(newCookieValue)
            .build();
    }

}
