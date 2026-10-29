package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.auth.infrastructure.JwtProperties;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 액세스 토큰은 서명 검증만으로 판단하고, 토큰이 무효하면 비회원으로 취급한다(조회는 통과).
 */
class AccessTokenFilterTest {

    static final String SECRET = "test-secret-key-test-secret-key-test-secret-key-0123";

    final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET));
    final AccessTokenFilter filter = new AccessTokenFilter(provider);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 유효한_액세스_쿠키면_회원_ID와_권한을_SecurityContext에_둔다() throws Exception {
        String token = provider.createAccessToken(42L, Role.ADMIN, LocalDateTime.now(TimeConfig.KST));
        var chain = new MockFilterChain();

        filter.doFilter(requestWith(new Cookie(AuthCookies.ACCESS_COOKIE, token)), new MockHttpServletResponse(), chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(42L);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 쿠키가_없으면_인증_정보_없이_요청을_통과시킨다() throws Exception {
        var chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 만료된_토큰이면_인증_정보_없이_요청을_통과시킨다() throws Exception {
        String expired = provider.createAccessToken(42L, Role.MEMBER, LocalDateTime.now(TimeConfig.KST).minusHours(1));
        var chain = new MockFilterChain();

        filter.doFilter(requestWith(new Cookie(AuthCookies.ACCESS_COOKIE, expired)), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 서명이_다른_토큰이면_인증_정보_없이_요청을_통과시킨다() throws Exception {
        var other = new JwtTokenProvider(new JwtProperties("other-secret-key-other-secret-key-other-secret-key-99"));
        String forged = other.createAccessToken(42L, Role.ADMIN, LocalDateTime.now(TimeConfig.KST));
        var chain = new MockFilterChain();

        filter.doFilter(requestWith(new Cookie(AuthCookies.ACCESS_COOKIE, forged)), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 액세스_쿠키에_리프레시_토큰을_보내면_인증_정보_없이_요청을_통과시킨다() throws Exception {
        String refresh = provider.createRefreshToken(42L, LocalDateTime.now(TimeConfig.KST));
        var chain = new MockFilterChain();

        filter.doFilter(requestWith(new Cookie(AuthCookies.ACCESS_COOKIE, refresh)), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletRequest requestWith(Cookie cookie) {
        var request = new MockHttpServletRequest();
        request.setCookies(cookie);
        return request;
    }

    @Test
    void 토큰_파싱_중_예상하지_못한_RuntimeException이_나도_인증_정보_없이_요청을_통과시킨다() throws Exception {
        var brokenProvider = mock(JwtTokenProvider.class);
        when(brokenProvider.parseAccessToken(anyString(), any())).thenThrow(new IllegalStateException("unexpected"));
        var brokenFilter = new AccessTokenFilter(brokenProvider);
        var chain = new MockFilterChain();

        brokenFilter.doFilter(requestWith(new Cookie(AuthCookies.ACCESS_COOKIE, "any-token")), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

}
