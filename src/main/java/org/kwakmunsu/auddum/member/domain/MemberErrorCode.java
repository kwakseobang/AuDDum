package org.kwakmunsu.auddum.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.support.error.ErrorCode;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

/** 회원 도메인의 오류 코드. */
@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND (HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다.", LogLevel.INFO),
    MEMBER_NOT_ACTIVE (HttpStatus.CONFLICT, "정상 상태의 회원이 아닙니다.", LogLevel.INFO),
    MEMBER_SUSPENDED (HttpStatus.FORBIDDEN, "정지된 회원은 이 요청을 할 수 없습니다.", LogLevel.INFO),

    ;

    private final HttpStatus status;
    private final String message;
    private final LogLevel logLevel;

}
