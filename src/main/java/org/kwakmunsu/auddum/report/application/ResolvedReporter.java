package org.kwakmunsu.auddum.report.application;

import org.kwakmunsu.auddum.report.domain.Reporter;

/** 결정된 신고자. 쿠키를 새로 발급했을 때만 {@code newCookieValue}가 채워지고, 아니면 null이다. */
public record ResolvedReporter(Reporter reporter, String newCookieValue) {

    /** 이미 정해진 신고자(회원, 유효한 쿠키). 쿠키를 새로 발급하지 않았다. */
    public static ResolvedReporter existing(Reporter reporter) {
        return new ResolvedReporter(reporter, null);
    }

}
