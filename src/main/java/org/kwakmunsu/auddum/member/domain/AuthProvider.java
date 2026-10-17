package org.kwakmunsu.auddum.member.domain;

import java.util.Locale;
import java.util.Optional;

/** 소셜 로그인 제공자. 값은 member_auth.provider에 문자열로 저장하고, 스프링 OAuth2 클라이언트의 registrationId(소문자)와 대응한다. */
public enum AuthProvider {

    KAKAO;

    /** registrationId(예: "kakao")에 해당하는 제공자를 찾는다. 등록되지 않은 제공자면 비어 있다. */
    public static Optional<AuthProvider> fromRegistrationId(String registrationId) {
        if (registrationId == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(valueOf(registrationId.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

}
