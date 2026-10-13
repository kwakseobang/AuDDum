package org.kwakmunsu.auddum.support.error;

/** 입력 검증 실패의 필드별 사유. 응답의 {@code errors} 항목이다. */
public record FieldViolation(String field, String reason) {
}
