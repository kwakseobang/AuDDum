package org.kwakmunsu.auddum.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.kwakmunsu.auddum.support.jpa.BaseEntity;

/**
 * 회원 애그리거트 루트. 소셜 연동(MemberAuth)은 member_auth가 회원을 참조하는 별도 엔티티다. 리프레시 토큰, 정지·탈퇴 전이 같은 상태 규칙은 M3의 다른 이슈(#14 등)에서 더한다.
 */
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Member extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    /** 회원 가입. 상태는 ACTIVE, 권한은 MEMBER다. */
    public static Member signUp() {
        Member member = new Member();

        member.status = MemberStatus.ACTIVE;
        member.role = Role.MEMBER;

        return member;
    }

    /** 로그인한 시각을 기록한다. 정지 회원도 로그인할 수 있고 상태는 바뀌지 않는다. */
    public void recordLogin(LocalDateTime now) {
        this.lastLoginAt = now;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

}
