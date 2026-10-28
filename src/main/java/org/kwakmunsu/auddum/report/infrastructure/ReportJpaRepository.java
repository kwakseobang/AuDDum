package org.kwakmunsu.auddum.report.infrastructure;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.kwakmunsu.auddum.report.domain.ReportStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportJpaRepository extends JpaRepository<Report, Long> {

    /** 관리자 목록. 상태가 같고 id가 커서보다 큰 신고를 접수 순(id 오름차순)으로 가져온다. 첫 페이지는 0을 넘긴다. */
    Slice<Report> findByStatusAndIdGreaterThanOrderByIdAsc(ReportStatus status, Long id, Pageable pageable);

    /** 같은 투표의 대기(PENDING) 신고를 모두 같은 결과로 종료한다. 감사 컬럼(updatedAt)과 낙관적 락 버전은 대량 갱신이 자동으로 올리지 않으므로 직접 갱신한다(다른 요청이 낡은 상태로 처리하지 못하게 한다). */
    @Modifying
    @Query("update Report r set r.status = :status, r.handledAt = :now, r.handledBy = :adminId, r.updatedAt = :now, r.version = r.version + 1"
        + " where r.pollId = :pollId and r.status = org.kwakmunsu.auddum.report.domain.ReportStatus.PENDING")
    int closePendingByPollId(
        @Param("pollId") Long pollId,
        @Param("status") ReportStatus status,
        @Param("adminId") Long adminId,
        @Param("now") LocalDateTime now
    );

    /** 같은 투표의 다른 신고를 접수 순으로 pageable이 정한 개수만큼 가져온다. */
    List<Report> findByPollIdAndIdNotOrderByIdAsc(Long pollId, Long excludeId, Pageable pageable);

    /** 투표별 신고 수(처리 상태와 무관). 신고가 없는 투표는 결과에 없다. */
    @Query("select new org.kwakmunsu.auddum.report.infrastructure.PollReportCount(r.pollId, count(r))"
        + " from Report r where r.pollId in :pollIds group by r.pollId")
    List<PollReportCount> countByPollIds(@Param("pollIds") Collection<Long> pollIds);

    boolean existsByPollIdAndReporterMemberId(Long pollId, Long memberId);

    boolean existsByPollIdAndReporterAnonId(Long pollId, UUID anonId);

    /**
     * 이 신고자가 이 투표를 이미 신고했는가. 임베디드 값 전체를 비교하면 비어 있는 쪽(NULL)이 같다고 판단되지 않으므로
     * 회원이면 회원 ID로, 비회원이면 익명 식별자로 찾는다.
     */
    default boolean existsByPollIdAndReporter(Long pollId, Reporter reporter) {
        if (reporter.memberId() != null) {
            return existsByPollIdAndReporterMemberId(pollId, reporter.memberId());
        }

        return existsByPollIdAndReporterAnonId(pollId, reporter.anonId());
    }

}
