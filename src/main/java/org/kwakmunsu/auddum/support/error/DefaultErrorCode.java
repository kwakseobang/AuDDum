package org.kwakmunsu.auddum.support.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

/** 도메인과 무관한 기본 오류 코드(API 명세 §7). */
@Getter
@RequiredArgsConstructor
public enum DefaultErrorCode implements ErrorCode {

    MALFORMED_REQUEST (HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.", LogLevel.INFO),
    VALIDATION_FAILED (HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", LogLevel.INFO),
    UNAUTHENTICATED   (HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.", LogLevel.WARN),
    FORBIDDEN         (HttpStatus.FORBIDDEN, "권한이 없습니다.", LogLevel.WARN),
    INTERNAL_ERROR    (HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", LogLevel.ERROR),

    ;

    private final HttpStatus status;
    private final String message;
    private final LogLevel logLevel;

}
