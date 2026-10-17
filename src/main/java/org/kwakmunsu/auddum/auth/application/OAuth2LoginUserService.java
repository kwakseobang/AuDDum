package org.kwakmunsu.auddum.auth.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.application.MemberCommandService;
import org.kwakmunsu.auddum.member.domain.AuthProvider;
import org.kwakmunsu.auddum.member.domain.Member;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 소셜 로그인한 사용자로 우리 회원을 찾거나 가입시킨다. 제공자는 요청의 registrationId로, 제공자의 사용자 식별자는
 * 클라이언트 설정의 user-name-attribute 값(카카오 id, 구글 sub 등)으로 구하므로 제공자를 늘려도 이 클래스는 바뀌지 않는다.
 * 제공자의 사용자 정보 조회는 delegate가 한다.
 */
@RequiredArgsConstructor
public class OAuth2LoginUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final MemberCommandService memberCommandService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider provider = AuthProvider.fromRegistrationId(registrationId)
            .orElseThrow(() -> new OAuth2AuthenticationException(
                new OAuth2Error("unsupported_provider", "지원하지 않는 로그인 제공자: " + registrationId, null)));

        String providerUserId = delegate.loadUser(userRequest).getName();
        Member member = memberCommandService.login(provider, providerUserId);

        return new AuddumOAuth2User(member.getId(), member.getRole());
    }

}
