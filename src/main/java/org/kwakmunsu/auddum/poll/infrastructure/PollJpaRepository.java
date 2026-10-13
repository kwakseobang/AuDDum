package org.kwakmunsu.auddum.poll.infrastructure;

import java.util.Optional;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollJpaRepository extends JpaRepository<Poll, Long> {

    boolean existsByPublicIdValue(String publicId);

    /** 작성자의 가장 최근 투표(삭제·숨김 포함). 생성 간격과 직전 동일 질문 판단에 쓴다. */
    Optional<Poll> findFirstByAuthorIdOrderByIdDesc(Long authorId);

}
