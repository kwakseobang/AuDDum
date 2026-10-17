package org.kwakmunsu.auddum.auth.api;

import org.kwakmunsu.auddum.auth.application.OAuth2LoginUserService;
import org.kwakmunsu.auddum.auth.application.RedirectPathValidator;
import org.kwakmunsu.auddum.member.application.MemberCommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 소셜 로그인(oauth2Login)만 설정한다. 지금은 카카오 하나이고, 제공자는 application.yaml의 클라이언트 등록과 AuthProvider에 추가한다. 로그인 시작과 콜백은 프레임워크가 제공한다. state와 돌아갈 경로는 로그인 과정에서만 HTTP 세션에 둔다.
 * 토큰으로 신원을 확인하는 필터와 쓰기 요청의 Origin 검사는 #13 이후에 더하며, 그 전에는 모든 요청을 허용한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        MemberCommandService memberCommandService,
        @Value("${auddum.frontend.base-url}") String frontendBaseUrl
    ) throws Exception {
        var userService = new OAuth2LoginUserService(new DefaultOAuth2UserService(), memberCommandService);
        var successHandler = new OAuth2LoginSuccessHandler(new CookieTokenIssuer(), new RedirectPathValidator(), frontendBaseUrl);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .oauth2Login(login -> login
                .authorizedClientRepository(new NoOpOAuth2AuthorizedClientRepository())
                .userInfoEndpoint(userInfo -> userInfo.userService(userService))
                .successHandler(successHandler)
                .failureHandler(new OAuth2LoginFailureHandler(frontendBaseUrl)))
            .addFilterBefore(new RedirectSavingFilter(), OAuth2AuthorizationRequestRedirectFilter.class);

        return http.build();
    }

}
