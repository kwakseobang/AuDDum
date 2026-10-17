package org.kwakmunsu.auddum.auth.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.kwakmunsu.auddum.member.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 소셜 로그인이 끝난 회원. 제공자의 정보는 담지 않고 우리 회원 ID와 권한만 가진다.
 */
public record AuddumOAuth2User(Long memberId, Role role) implements OAuth2User {

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("memberId", memberId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }

}
