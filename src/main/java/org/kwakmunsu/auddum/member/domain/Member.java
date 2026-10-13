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
 * 회원 애그리거트 루트. 지금은 투표 작성자로 쓰이는 최소 형태이고, 카카오 연동·리프레시 토큰·정지·탈퇴 행위는 M3에서 더한다.
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

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

}
