package org.kwakmunsu.auddum.member.infrastructure;

import org.kwakmunsu.auddum.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {

}
