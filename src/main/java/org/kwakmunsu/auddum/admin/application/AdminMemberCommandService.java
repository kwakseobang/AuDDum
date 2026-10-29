package org.kwakmunsu.auddum.admin.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.admin.domain.AdminAction;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.CommandService;

/** 관리자의 회원 조치. 회원 상태 변경과 이력 기록을 한 트랜잭션으로 처리하고, 실패하면 전부 롤백한다. */
@RequiredArgsConstructor
@CommandService
public class AdminMemberCommandService {

    private final AdminAccessValidator adminAccessValidator;
    private final MemberReader memberReader;
    private final AdminActionWriter adminActionWriter;

    /** 정상 회원을 정지한다. 그 회원의 기존 투표와 신고 상태는 바꾸지 않는다. 이미 정지·탈퇴한 회원은 409, 없는 회원은 404다. */
    public void suspend(Long adminId, Long memberId, String reason) {
        adminAccessValidator.validateAdmin(adminId);

        Member member = memberReader.find(memberId);
        // 사유 검증(400)을 대상 상태 확인(409·404)보다 먼저 하려고 이력을 먼저 만든다.
        AdminAction history = AdminAction.suspendMember(adminId, memberId, reason);

        member.suspend(LocalDateTime.now(TimeConfig.KST));

        adminActionWriter.record(history);
    }

}
