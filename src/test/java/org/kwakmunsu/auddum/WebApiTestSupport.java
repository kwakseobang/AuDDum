package org.kwakmunsu.auddum;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.application.RefreshTokenService;
import org.kwakmunsu.auddum.member.application.MemberCommandService;
import org.kwakmunsu.auddum.poll.application.PollCommandService;
import org.kwakmunsu.auddum.poll.application.PollQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * 컨트롤러(웹 계층) 테스트의 공통 설정. 슬라이스에는 로그인(oauth2Login) 설정이 필요 없어 OAuth2 클라이언트 자동 설정은 뺀다. 컨트롤러가 의존하는 서비스는 여기서 {@code @MockitoBean}으로 대체한다.
 */
@Import(AuthCookies.class)
@WebMvcTest(excludeAutoConfiguration = {OAuth2ClientAutoConfiguration.class, OAuth2ClientWebSecurityAutoConfiguration.class})
public abstract class WebApiTestSupport {

    @Autowired
    protected MockMvcTester mvcTester;

    @Autowired
    protected JsonMapper jsonMapper;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /** 액세스 토큰 필터가 확인한 로그인 회원을 흉내 낸다. 슬라이스에는 필터가 없어 인증 정보를 직접 둔다. */
    protected static RequestPostProcessor loginAs(Long memberId) {
        return request -> {
            SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

            return request;
        };
    }

    @MockitoBean
    protected PollCommandService pollCommandService;

    @MockitoBean
    protected PollQueryService pollQueryService;

    @MockitoBean
    protected RefreshTokenService refreshTokenService;

    @MockitoBean
    protected MemberCommandService memberCommandService;

}
