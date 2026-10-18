package org.kwakmunsu.auddum.auth.api;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.auth.application.OAuth2LoginUserService;
import org.kwakmunsu.auddum.auth.application.RedirectPathValidator;
import org.kwakmunsu.auddum.auth.application.RefreshTokenService;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.application.MemberCommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 소셜 로그인(oauth2Login)과 토큰 인증을 설정한다. 지금은 카카오 하나이고, 제공자는 application.yaml의 클라이언트 등록과 AuthProvider에 추가한다. 로그인 시작과 콜백은 프레임워크가 제공한다. state와 돌아갈 경로는 로그인 과정에서만 HTTP 세션에 둔다.
 * 요청마다 액세스 토큰 쿠키로 신원을 확인하고(AccessTokenFilter) 쓰기 요청은 Origin을 검사한다(OriginCheckFilter). 허용 Origin 목록은 CORS 설정과 Origin 검사가 같이 쓴다.
 * 로그인이 필요한지는 필터가 아니라 컨트롤러의 {@code @CurrentMemberId}가 판단하므로 여기서는 모든 요청을 통과시킨다.
 */
@RequiredArgsConstructor
@Configuration
public class SecurityConfig {

    private final MemberCommandService memberCommandService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookies authCookies;

    @Value("${auddum.frontend.base-url}")
    private String frontendBaseUrl;

    /** 프런트 주소 말고 더 허용할 Origin(쉼표 구분, 선택). 프런트 주소의 Origin은 항상 허용한다. */
    @Value("${auddum.cors.allowed-origins:}")
    private List<String> additionalOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var userService = new OAuth2LoginUserService(new DefaultOAuth2UserService(), memberCommandService);
        var successHandler = new OAuth2LoginSuccessHandler(refreshTokenService, authCookies, new RedirectPathValidator(), frontendBaseUrl);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable);

        List<String> allowedOrigins = allowedOrigins();

        http
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource(allowedOrigins)));

        http
            .oauth2Login(login -> login
                .authorizedClientRepository(new NoOpOAuth2AuthorizedClientRepository())
                .userInfoEndpoint(userInfo -> userInfo.userService(userService))
                .successHandler(successHandler)
                .failureHandler(new OAuth2LoginFailureHandler(frontendBaseUrl)));

        http
            .addFilterBefore(new RedirectSavingFilter(), OAuth2AuthorizationRequestRedirectFilter.class)
            .addFilterBefore(new AccessTokenFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new OriginCheckFilter(allowedOrigins), CorsFilter.class);

        return http.build();
    }

    /** 허용 Origin 목록. 프런트 주소와 추가 목록을 Origin 형태로 한 번만 맞춰 CORS 설정과 Origin 검사가 같이 쓴다. */
    private List<String> allowedOrigins() {
        return Stream.concat(Stream.of(frontendBaseUrl), additionalOrigins.stream())
            .map(String::strip)
            .filter(url -> !url.isEmpty())
            .map(SecurityConfig::toOrigin)
            .distinct()
            .toList();
    }

    private static String toOrigin(String url) {
        try {
            return OriginCheckFilter.toOrigin(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "auddum.frontend.base-url(FRONTEND_BASE_URL) 또는 auddum.cors.allowed-origins(CORS_ALLOWED_ORIGINS)에 "
                    + "스킴과 호스트가 있는 주소를 쉼표로 구분해 넣어야 한다: " + url, e);
        }
    }

    /** 허용 Origin에만 쿠키를 실은 요청을 허용한다. 와일드카드는 쓰지 않는다. */
    private CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }

}
