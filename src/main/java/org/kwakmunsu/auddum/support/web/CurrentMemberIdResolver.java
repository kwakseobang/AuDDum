package org.kwakmunsu.auddum.support.web;

import org.jspecify.annotations.NonNull;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 임시 신원. X-Member-Id 헤더의 숫자를 회원 ID로 쓴다. 없거나 숫자가 아니면 401이다.
 * 인증(#13)이 들어오면 이 클래스만 인증 정보에서 회원 ID를 꺼내도록 바꾼다.
 */
public class CurrentMemberIdResolver implements HandlerMethodArgumentResolver {

    static final String HEADER = "X-Member-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMemberId.class);
    }

    @Override
    public Object resolveArgument(
        @NonNull MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        String header = webRequest.getHeader(HEADER);
        if (header == null) {
            throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED);
        }

        try {
            return Long.valueOf(header.strip());
        } catch (NumberFormatException e) {
            throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED, e);
        }
    }

}
