package org.kwakmunsu.auddum.admin.infrastructure;

import java.util.Collection;
import java.util.List;
import org.kwakmunsu.auddum.admin.domain.ActionType;
import org.kwakmunsu.auddum.admin.domain.AdminAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminActionJpaRepository extends JpaRepository<AdminAction, Long> {

    /** 신고 상세에 보일 조치: 이 신고를 기각한 이력과 대상 투표에 한 숨김·삭제 이력을 기록 순으로 가져온다. */
    @Query("select a from AdminAction a where a.reportId = :reportId"
        + " or (a.targetPollId = :pollId and a.type in :pollActionTypes) order by a.id")
    List<AdminAction> findForReport(
        @Param("reportId") Long reportId,
        @Param("pollId") Long pollId,
        @Param("pollActionTypes") Collection<ActionType> pollActionTypes
    );

}
