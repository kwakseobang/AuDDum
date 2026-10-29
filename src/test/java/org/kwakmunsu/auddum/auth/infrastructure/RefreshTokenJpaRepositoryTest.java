package org.kwakmunsu.auddum.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.auth.domain.RefreshToken;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동시 재발급 직렬화: 같은 토큰의 재발급은 행을 잠그고 읽는다.
 * 여기서는 해시로 조회되는지(FOR UPDATE 쿼리가 H2에서 동작하는지)만 확인하고, 실제 경합은 검증하지 않는다.
 */
@Transactional
@RequiredArgsConstructor
class RefreshTokenJpaRepositoryTest extends IntegrationTestSupport {

    final RefreshTokenJpaRepository repository;
    final MemberJpaRepository memberJpaRepository;

    @Test
    void 해시로_잠금_조회하면_해당_토큰을_돌려준다() {
        String hash = "a".repeat(64);
        var saved = repository.save(RefreshToken.issue(createMemberId(), hash, LocalDateTime.of(2026, 10, 31, 12, 0)));

        var found = repository.findForUpdate(hash);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void 없는_해시로_잠금_조회하면_비어_있다() {
        assertThat(repository.findForUpdate("b".repeat(64))).isEmpty();
    }

    private Long createMemberId() {
        return memberJpaRepository.save(Member.signUp()).getId();
    }

}
