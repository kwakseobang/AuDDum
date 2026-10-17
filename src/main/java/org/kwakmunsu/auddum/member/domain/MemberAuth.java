package org.kwakmunsu.auddum.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.kwakmunsu.auddum.support.jpa.BaseEntity;

/** 회원의 외부 로그인 연동 정보. 탈퇴하면 삭제해 같은 계정으로 다시 가입하면 새 회원이 되게 한다. */
@Table(name = "member_auth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class MemberAuth extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 64)
    private String providerUserId;

    public static MemberAuth of(Member member, AuthProvider provider, String providerUserId) {
        MemberAuth auth = new MemberAuth();
        auth.member = member;
        auth.provider = provider;
        auth.providerUserId = providerUserId;

        return auth;
    }

}
