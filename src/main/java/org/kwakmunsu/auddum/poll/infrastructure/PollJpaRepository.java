package org.kwakmunsu.auddum.poll.infrastructure;

import java.util.Optional;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollJpaRepository extends JpaRepository<Poll, Long> {

    boolean existsByPublicIdValue(String publicId);

    /**
     * 작성자의 가장 최근 투표(삭제·숨김 포함). 생성 간격과 직전 동일 질문 판단에 쓴다.
     */
    Optional<Poll> findFirstByAuthorIdOrderByIdDesc(Long authorId);

    /**
     * 상세 조회용. 컨트롤러가 트랜잭션 밖에서 선택지를 읽으므로 함께 가져온다.
     */
    @Query("select p from Poll p left join fetch p.options where p.publicId.value = :publicId")
    Optional<Poll> findByPublicId(@Param("publicId") String publicId);

    /**
     * 목록 조회. id가 주어진 값보다 작은(더 오래된) 투표를 최신순으로 가져온다. 첫 페이지는 Long.MAX_VALUE를 넘긴다.
     */
    @Query("select p from Poll p"
        + " where p.status = :status and p.id < :id"
        + " order by p.id desc"
    )
    Slice<Poll> findOlderThan(@Param("status") PollStatus status, @Param("id") Long id, Pageable pageable);

}
