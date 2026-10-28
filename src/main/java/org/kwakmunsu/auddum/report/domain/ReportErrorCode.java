package org.kwakmunsu.auddum.report.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.support.error.ErrorCode;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

/** 신고 도메인의 오류 코드(API 명세 §7). */
@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    REPORT_NOT_FOUND (HttpStatus.NOT_FOUND, "존재하지 않는 신고입니다.", LogLevel.INFO),
    REPORT_ALREADY_HANDLED (HttpStatus.CONFLICT, "이미 처리된 신고입니다.", LogLevel.INFO),
    REPORT_ALREADY_SUBMITTED (HttpStatus.CONFLICT, "이미 신고한 투표입니다.", LogLevel.INFO),

    ;

    private final HttpStatus status;
    private final String message;
    private final LogLevel logLevel;

}
