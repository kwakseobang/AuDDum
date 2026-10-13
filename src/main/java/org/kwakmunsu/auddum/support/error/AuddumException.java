package org.kwakmunsu.auddum.support.error;

import lombok.Getter;

/** 오류 코드를 가진 프로젝트 공통 예외. 전역 핸들러가 ProblemDetail로 바꾼다. */
@Getter
public class AuddumException extends RuntimeException {

    private final ErrorCode errorCode;

    public AuddumException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AuddumException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

}
