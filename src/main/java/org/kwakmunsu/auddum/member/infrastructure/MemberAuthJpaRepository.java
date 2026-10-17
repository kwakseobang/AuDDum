package org.kwakmunsu.auddum.member.infrastructure;

import java.util.Optional;
import org.kwakmunsu.auddum.member.domain.AuthProvider;
import org.kwakmunsu.auddum.member.domain.MemberAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberAuthJpaRepository extends JpaRepository<MemberAuth, Long> {

    /** 로그인은 연동을 찾은 뒤 곧바로 회원의 로그인 시각을 바꾸므로, 회원을 다시 조회하지 않도록 함께 가져온다. */
    @Query("select a from MemberAuth a join fetch a.member"
        + " where a.provider = :provider and a.providerUserId = :providerUserId"
    )
    Optional<MemberAuth> findByProviderAndProviderUserId(
        @Param("provider") AuthProvider provider,
        @Param("providerUserId") String providerUserId
    );

    @Modifying
    @Query("delete from MemberAuth a where a.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

}
