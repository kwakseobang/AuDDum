package org.kwakmunsu.auddum.auth.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.application.MemberCommandService;
import org.kwakmunsu.auddum.member.domain.AuthProvider;
import org.kwakmunsu.auddum.member.domain.Member;
import org.springframework.dao.DataIntegrityViolationException;
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
        Member member = login(provider, providerUserId);

        return new AuddumOAuth2User(member.getId(), member.getRole());
    }

    /**
     * 같은 계정의 첫 로그인이 동시에 들어오면 한쪽의 가입이 유니크 제약으로 실패한다. 실패한 쪽은 다른 쪽이 만든 회원을 다시 조회해 로그인한다.
     * 그 밖의 서버 오류(DB 장애 등)는 로그인 실패로 바꿔 실패 핸들러가 프런트 오류 화면으로 보내게 한다.
     */
    private Member login(AuthProvider provider, String providerUserId) {
        try {
            return memberCommandService.login(provider, providerUserId);
        } catch (DataIntegrityViolationException e) {
            return retryLogin(provider, providerUserId);
        } catch (RuntimeException e) {
            throw serverError(e);
        }
    }

    private Member retryLogin(AuthProvider provider, String providerUserId) {
        try {
            return memberCommandService.login(provider, providerUserId);
        } catch (RuntimeException e) {
            throw serverError(e);
        }
    }

    private OAuth2AuthenticationException serverError(RuntimeException cause) {
        return new OAuth2AuthenticationException(new OAuth2Error("server_error", "로그인 처리 중 서버 오류가 발생했다.", null), cause);
    }

}
