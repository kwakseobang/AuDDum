package org.kwakmunsu.auddum.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.application.MemberCommandService;
import org.kwakmunsu.auddum.member.domain.AuthProvider;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-명세 콜백: OAuth2UserService가 소셜 계정으로 회원을 조회·가입시킨다.
 * 제공자는 요청의 registrationId로, 제공자의 사용자 식별자는 user-name-attribute 값(카카오는 id)으로 구한다.
 */
@RequiredArgsConstructor
@Transactional
class OAuth2LoginUserServiceTest extends IntegrationTestSupport {

    static final long KAKAO_ID = 424242L;

    final MemberCommandService memberCommandService;
    final MemberJpaRepository memberJpaRepository;
    final MemberAuthJpaRepository memberAuthJpaRepository;

    @Test
    void 소셜_사용자_정보의_식별자로_회원을_가입시키고_회원_ID와_권한을_담은_principal을_돌려준다() {
        var countBefore = memberJpaRepository.count();
        var service = new OAuth2LoginUserService(delegate(KAKAO_ID), memberCommandService);

        var principal = (AuddumOAuth2User) service.loadUser(request("kakao"));

        assertThat(memberJpaRepository.count()).isEqualTo(countBefore + 1);
        assertThat(principal.role()).isEqualTo(Role.MEMBER);
        var auth = memberAuthJpaRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, String.valueOf(KAKAO_ID)).orElseThrow();
        assertThat(auth.getMember().getId()).isEqualTo(principal.memberId());
    }

    @Test
    void 같은_계정으로_두_번_호출하면_같은_회원의_principal을_돌려준다() {
        var service = new OAuth2LoginUserService(delegate(KAKAO_ID), memberCommandService);

        var first = (AuddumOAuth2User) service.loadUser(request("kakao"));
        var second = (AuddumOAuth2User) service.loadUser(request("kakao"));

        assertThat(second.memberId()).isEqualTo(first.memberId());
    }

    @Test
    void 지원하지_않는_제공자면_회원을_만들지_않고_실패한다() {
        var countBefore = memberJpaRepository.count();
        var service = new OAuth2LoginUserService(delegate(KAKAO_ID), memberCommandService);

        assertThatThrownBy(() -> service.loadUser(request("unknown")))
            .isInstanceOf(OAuth2AuthenticationException.class);

        assertThat(memberJpaRepository.count()).isEqualTo(countBefore);
    }

    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate(long providerUserId) {
        return request -> new DefaultOAuth2User(List.of(), Map.of("id", providerUserId), "id");
    }

    private OAuth2UserRequest request(String registrationId) {
        var registration = ClientRegistration.withRegistrationId(registrationId)
            .clientId("client")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/" + registrationId)
            .authorizationUri("https://example.com/authorize")
            .tokenUri("https://example.com/token")
            .userInfoUri("https://example.com/me")
            .userNameAttributeName("id")
            .build();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(60));

        return new OAuth2UserRequest(registration, token);
    }

}
