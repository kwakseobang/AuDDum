package org.kwakmunsu.auddum.support.error;

import java.util.List;
import lombok.Getter;

/**
 * 오류 코드를 가진 프로젝트 공통 예외. 전역 핸들러가 ProblemDetail로 바꾼다.
 * 입력 검증 실패는 {@link #validation}으로 만들어 어느 필드가 왜 틀렸는지 {@code errors}에 담고, 예외 메시지는 그 사유다.
 */
@Getter
public class AuddumException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldViolation> errors;

    public AuddumException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.errors = List.of();
    }

    public AuddumException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.errors = List.of();
    }

    private AuddumException(ErrorCode errorCode, List<FieldViolation> errors) {
        super(errors.getFirst().reason());
        this.errorCode = errorCode;
        this.errors = errors;
    }

    /** 입력 검증 실패(VALIDATION_FAILED). 응답의 errors에 필드와 사유가 담긴다. */
    public static AuddumException validation(String field, String reason) {
        return new AuddumException(DefaultErrorCode.VALIDATION_FAILED, List.of(new FieldViolation(field, reason)));
    }

}
