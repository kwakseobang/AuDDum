package org.kwakmunsu.auddum.support.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 인증-중복-참여 §1 / API-명세 §7: 로그인한 회원의 ID는 인증된 신원에서 꺼내고, 로그인하지 않았으면 401 UNAUTHENTICATED다.
 */
class CurrentMemberIdResolverTest {

    final CurrentMemberIdResolver resolver = new CurrentMemberIdResolver();
    final MethodParameter parameter = mock(MethodParameter.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증된_회원이_있으면_회원_ID를_돌려준다() {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        assertThat(resolver.resolveArgument(parameter, null, null, null)).isEqualTo(7L);
    }

    @Test
    void 인증된_회원이_없으면_UNAUTHENTICATED다() {
        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, null, null))
            .isInstanceOfSatisfying(AuddumException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(DefaultErrorCode.UNAUTHENTICATED));
    }

}
