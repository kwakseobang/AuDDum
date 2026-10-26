package org.kwakmunsu.auddum.support.error;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 오류를 ProblemDetail(RFC 7807)로 내리고 확장 필드 {@code code}·{@code errors}를 덧붙인다. Spring MVC 표준 예외의 변환은 부모 클래스에 맡긴다.
 */
@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE = "code";
    private static final String ERRORS = "errors";

    @ExceptionHandler(AuddumException.class)
    ResponseEntity<Object> handleAuddumException(AuddumException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        logByLevel(errorCode.getLogLevel(), "[GlobalExceptionHandler] 예외 응답 - code=%s, message=%s".formatted(errorCode.name(), ex.getMessage()), ex);

        ProblemDetail problem = problemOf(errorCode);
        if (!ex.getErrors().isEmpty()) {
            problem.setProperty(ERRORS, ex.getErrors());
        }

        return ResponseEntity.status(errorCode.getStatus()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        logByLevel(LogLevel.ERROR, "[GlobalExceptionHandler] 처리되지 않은 예외", ex);

        return ResponseEntity.status(DefaultErrorCode.INTERNAL_ERROR.getStatus())
            .body(problemOf(DefaultErrorCode.INTERNAL_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        @NonNull HttpHeaders headers,
        @NonNull HttpStatusCode status,
        @NonNull WebRequest request
    ) {
        ProblemDetail problem = problemOf(DefaultErrorCode.VALIDATION_FAILED);

        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
            .toList();

        problem.setProperty(ERRORS, violations);
        logByLevel(LogLevel.INFO, "[GlobalExceptionHandler] 입력 검증 실패 - code=%s, errors=%s".formatted(DefaultErrorCode.VALIDATION_FAILED.name(), violations), ex);

        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * 부모가 만든 400 응답(본문 형식 오류, 타입 불일치 등)에 코드가 없으면 MALFORMED_REQUEST를 붙인다.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
        @NonNull Exception ex,
        @Nullable Object body,
        @NonNull HttpHeaders headers,
        @NonNull HttpStatusCode statusCode,
        @NonNull WebRequest request
    ) {
        if (!hasLoggedBySpecificHandler(body)) {
            logByLevel(logLevelOf(statusCode), "[GlobalExceptionHandler] 예외 응답 - type=%s, message=%s".formatted(ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);

        if (response != null && response.getBody() instanceof ProblemDetail problem
            && statusCode.value() == DefaultErrorCode.MALFORMED_REQUEST.getStatus().value()
            && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
            problem.setProperty(CODE, DefaultErrorCode.MALFORMED_REQUEST.name());
        }

        return response;
    }

    /** code가 이미 붙은 본문은 호출한 핸들러가 구체적인 메시지로 로그를 남긴 것이다. */
    private boolean hasLoggedBySpecificHandler(@Nullable Object body) {
        return body instanceof ProblemDetail problem
            && problem.getProperties() != null
            && problem.getProperties().containsKey(CODE);
    }

    private LogLevel logLevelOf(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return LogLevel.ERROR;
        }
        return status.value() == DefaultErrorCode.MALFORMED_REQUEST.getStatus().value() ? LogLevel.INFO : LogLevel.WARN;
    }

    private void logByLevel(LogLevel level, String message, Exception ex) {
        switch (level) {
            case ERROR -> log.error(message, ex);
            case WARN -> log.warn(message, ex);
            default -> log.info(message, ex);
        }
    }

    private ProblemDetail problemOf(ErrorCode errorCode) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
        problem.setProperty(CODE, errorCode.name());
        return problem;
    }

}
