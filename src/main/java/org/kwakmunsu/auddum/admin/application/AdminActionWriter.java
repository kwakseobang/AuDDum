package org.kwakmunsu.auddum.admin.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.admin.domain.AdminAction;
import org.kwakmunsu.auddum.admin.infrastructure.AdminActionJpaRepository;
import org.springframework.stereotype.Component;

/** 관리자 조치 이력 쓰기. 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여하므로, 조치가 롤백되면 이력도 함께 사라진다. */
@Slf4j
@RequiredArgsConstructor
@Component
public class AdminActionWriter {

    private final AdminActionJpaRepository adminActionRepository;

    public AdminAction record(AdminAction action) {
        AdminAction saved = adminActionRepository.save(action);

        log.info("[AdminActionWriter] 조치 기록 - type={}, adminId={}, reportId={}, pollId={}, memberId={}",
            saved.getType(),
            saved.getAdminId(),
            saved.getReportId(),
            saved.getTargetPollId(),
            saved.getTargetMemberId()
        );

        return saved;
    }

}
