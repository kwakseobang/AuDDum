package org.kwakmunsu.auddum.support.web;

import org.jspecify.annotations.NonNull;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 인증된 신원(SecurityContext)에서 회원 ID를 꺼낸다. 토큰을 확인하는 곳은 auth의 AccessTokenFilter이고, 로그인하지 않았으면 401이다. {@code @OptionalMemberId}는 대신 null을 준다.
 */
public class CurrentMemberIdResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMemberId.class) || parameter.hasParameterAnnotation(OptionalMemberId.class);
    }

    @Override
    public Object resolveArgument(
        @NonNull MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        @NonNull NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Long memberId) {
            return memberId;
        }
        if (parameter.hasParameterAnnotation(OptionalMemberId.class)) {
            return null;
        }

        throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED);
    }

}
