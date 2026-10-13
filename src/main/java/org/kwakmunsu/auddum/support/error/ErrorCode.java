package org.kwakmunsu.auddum.support.error;

import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

/**
 * 오류 코드. 응답의 {@code code}는 {@link #name()}이다.
 * 공통 코드는 {@link DefaultErrorCode}, 도메인 코드는 각 도메인이 이 인터페이스를 구현한 enum으로 둔다.
 */
public interface ErrorCode {

    String name();

    HttpStatus getStatus();

    String getMessage();

    LogLevel getLogLevel();

}
