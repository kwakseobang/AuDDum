package org.kwakmunsu.auddum.auth.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.kwakmunsu.auddum.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 같은 토큰으로 동시에 재발급해도 한 요청씩 처리되도록 행을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByMemberId(Long memberId);

    @Modifying
    @Query("delete from RefreshToken t where t.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);

}
