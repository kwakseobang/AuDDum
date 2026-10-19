package org.kwakmunsu.auddum.vote.infrastructure;

import org.kwakmunsu.auddum.vote.domain.AnonId;

/** 새로 발급한 익명 쿠키. {@code value}가 쿠키 값(v1.&lt;UUID&gt;.&lt;서명&gt;)이다. */
public record AnonCookie(AnonId anonId, String value) {

}
