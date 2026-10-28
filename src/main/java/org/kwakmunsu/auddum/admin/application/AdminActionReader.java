package org.kwakmunsu.auddum.admin.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.admin.domain.ActionType;
import org.kwakmunsu.auddum.admin.domain.AdminAction;
import org.kwakmunsu.auddum.admin.infrastructure.AdminActionJpaRepository;
import org.kwakmunsu.auddum.report.domain.Report;
import org.springframework.stereotype.Component;

/** 관리자 조치 이력 읽기. 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여한다. */
@RequiredArgsConstructor
@Component
public class AdminActionReader {

    private final AdminActionJpaRepository adminActionRepository;

    /** 신고 상세에 보일 조치를 기록 순으로 돌려준다: 이 신고의 기각 이력과 대상 투표에 한 숨김·삭제 이력이다. */
    public List<AdminAction> findByReport(Report report) {
        return adminActionRepository.findForReport(
            report.getId(),
            report.getPollId(),
            List.of(ActionType.HIDE_POLL, ActionType.DELETE_POLL)
        );
    }

}
