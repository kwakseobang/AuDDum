package org.kwakmunsu.auddum.admin.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.admin.domain.AdminAction;
import org.kwakmunsu.auddum.admin.domain.PollTakedown;
import org.kwakmunsu.auddum.poll.application.PollReader;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.application.ReportWriter;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.CommandService;

/** 관리자의 투표 조치. 투표 상태 변경, 그 투표의 접수 신고 종료, 이력 기록을 한 트랜잭션으로 처리하고, 실패하면 전부 롤백한다. */
@RequiredArgsConstructor
@CommandService
public class AdminPollCommandService {

    private final AdminAccessValidator adminAccessValidator;
    private final PollReader pollReader;
    private final ReportWriter reportWriter;
    private final AdminActionWriter adminActionWriter;

    /**
     * 투표를 숨기거나 삭제하고 그 투표의 대기 신고를 모두 조치 완료로 종료한다.
     * 숨김은 공개 투표만 가능하고(이미 숨김이면 409), 삭제는 공개·숨김 투표에서 가능하다. 삭제된 투표는 404다.
     */
    public void takedown(Long adminId, String pollPublicId, PollTakedown takedown, String reason) {
        adminAccessValidator.validateAdmin(adminId);

        Poll poll = pollReader.find(pollPublicId);
        poll.validateNotDeleted();
        // 사유 검증(400)을 대상 상태 확인(409·404)보다 먼저 하려고 이력을 먼저 만든다.
        AdminAction history = AdminAction.takedownPoll(adminId, poll.getId(), takedown, reason);

        takedown.apply(poll);

        adminActionWriter.record(history);
        reportWriter.resolvePendingOfPoll(poll.getId(), adminId, LocalDateTime.now(TimeConfig.KST));
    }

}
