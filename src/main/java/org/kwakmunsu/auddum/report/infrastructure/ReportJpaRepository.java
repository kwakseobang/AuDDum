package org.kwakmunsu.auddum.report.infrastructure;

import java.util.UUID;
import org.kwakmunsu.auddum.report.domain.Report;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportJpaRepository extends JpaRepository<Report, Long> {

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
