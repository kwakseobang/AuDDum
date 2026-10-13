package org.kwakmunsu.auddum.poll.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.support.error.ErrorCode;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

/** 투표 도메인의 오류 코드(API 명세 §7). */
@Getter
@RequiredArgsConstructor
public enum PollErrorCode implements ErrorCode {

    INVALID_OPTION     (HttpStatus.BAD_REQUEST, "존재하지 않거나 이 투표에 속하지 않는 선택지입니다.", LogLevel.INFO),
    POLL_NOT_DELETABLE (HttpStatus.FORBIDDEN, "관리자가 숨긴 투표는 삭제할 수 없습니다.", LogLevel.INFO),
    POLL_NOT_FOUND     (HttpStatus.NOT_FOUND, "존재하지 않는 투표입니다.", LogLevel.INFO),
    POLL_CLOSED        (HttpStatus.CONFLICT, "마감된 투표입니다.", LogLevel.INFO),
    POLL_NOT_HIDDEN    (HttpStatus.CONFLICT, "숨김 상태의 투표가 아닙니다.", LogLevel.INFO),

    ;

    private final HttpStatus status;
    private final String message;
    private final LogLevel logLevel;

}
