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
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.support.jpa.BaseEntity;

/**
 * 회원 애그리거트 루트. 소셜 연동(MemberAuth)은 member_auth가 회원을 참조하는 별도 엔티티다. 리프레시 토큰은 auth가 가진다.
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

    /** 탈퇴. 정지 중에도 할 수 있고, 이미 탈퇴한 회원이면 401이다. 소셜 연동·리프레시 토큰 삭제는 서비스가 같은 트랜잭션에서 함께 한다. */
    public void withdraw(LocalDateTime now) {
        validateNotWithdrawn();

        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = now;
    }

    /** 정상 회원만 통과시킨다. 탈퇴했으면 401, 정지면 403이다. 정지 회원이 할 수 있는 탈퇴·로그아웃에서는 부르지 않는다. */
    public void validateActive() {
        validateNotWithdrawn();
        if (isSuspended()) {
            throw new AuddumException(MemberErrorCode.MEMBER_SUSPENDED);
        }
    }

    /** 탈퇴한 회원이면 401이다. */
    public void validateNotWithdrawn() {
        if (isWithdrawn()) {
            throw new AuddumException(DefaultErrorCode.UNAUTHENTICATED);
        }
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return status == MemberStatus.SUSPENDED;
    }

    public boolean isWithdrawn() {
        return status == MemberStatus.WITHDRAWN;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** 관리자 권한이 있는지 본다. role이 ADMIN이어도 탈퇴한 회원은 없다. 정지된 관리자의 처리는 관리자 API(#52)에서 정한다. */
    public boolean hasAdminAuthority() {
        return isAdmin() && !isWithdrawn();
    }

}
