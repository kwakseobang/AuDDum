package org.kwakmunsu.auddum.vote.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.support.error.ErrorCode;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

/** 참여 도메인의 오류 코드(API 명세 §7). */
@Getter
@RequiredArgsConstructor
public enum VoteErrorCode implements ErrorCode {

    ALREADY_VOTED (HttpStatus.CONFLICT, "이미 참여한 투표입니다.", LogLevel.INFO),

    ;

    private final HttpStatus status;
    private final String message;
    private final LogLevel logLevel;

}
