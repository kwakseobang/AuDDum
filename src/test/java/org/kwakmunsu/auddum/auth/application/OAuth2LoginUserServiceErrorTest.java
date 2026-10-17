package org.kwakmunsu.auddum.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.application.MemberCommandService;
import org.kwakmunsu.auddum.member.domain.AuthProvider;
import org.kwakmunsu.auddum.member.domain.Member;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

/** 로그인 처리 중의 오류: 동시 첫 로그인의 유니크 위반은 다시 조회해 로그인하고, 서버 오류는 로그인 실패(OAuth2AuthenticationException)로 바꾼다. */
class OAuth2LoginUserServiceErrorTest {

    final MemberCommandService memberCommandService = mock(MemberCommandService.class);
    final OAuth2LoginUserService service = new OAuth2LoginUserService(
        request -> new DefaultOAuth2User(List.of(), Map.of("id", 1L), "id"), memberCommandService);

    @Test
    void 동시_첫_로그인으로_유니크_제약에_걸리면_다시_조회해_로그인한다() {
        var member = member(7L);
        given(memberCommandService.login(eq(AuthProvider.KAKAO), any()))
            .willThrow(new DataIntegrityViolationException("uq_member_auth_provider_user"))
            .willReturn(member);

        var principal = (AuddumOAuth2User) service.loadUser(request());

        assertThat(principal.memberId()).isEqualTo(7L);
        verify(memberCommandService, times(2)).login(eq(AuthProvider.KAKAO), any());
    }

    @Test
    void DB_장애_같은_서버_오류는_로그인_실패로_바꾼다() {
        given(memberCommandService.login(eq(AuthProvider.KAKAO), any())).willThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> service.loadUser(request()))
            .isInstanceOf(OAuth2AuthenticationException.class)
            .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
            .isEqualTo("server_error");
    }

    private Member member(Long id) {
        var member = Member.signUp();
        ReflectionTestUtils.setField(member, "id", id);

        return member;
    }

    private OAuth2UserRequest request() {
        var registration = ClientRegistration.withRegistrationId("kakao")
            .clientId("client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/kakao")
            .authorizationUri("https://example.com/authorize")
            .tokenUri("https://example.com/token")
            .userInfoUri("https://example.com/me")
            .userNameAttributeName("id")
            .build();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(60));

        return new OAuth2UserRequest(registration, token);
    }

}
