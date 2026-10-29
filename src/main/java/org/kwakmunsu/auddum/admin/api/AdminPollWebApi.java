package org.kwakmunsu.auddum.admin.api;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.admin.api.dto.AdminPollTakedownRequest;
import org.kwakmunsu.auddum.admin.application.AdminPollCommandService;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.CurrentMemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 관리자 투표 API. 미로그인은 {@code @CurrentMemberId}가 401로 막고, 관리자 권한은 서비스가 확인한다. */
@RequestMapping("/api/v1/admin/polls")
@RequiredArgsConstructor
@WebApi
public class AdminPollWebApi extends AdminPollWebApiDocs {

    private final AdminPollCommandService adminPollCommandService;

    @Override
    @PatchMapping("/{pollId}/status")
    public ResponseEntity<Void> takedownPoll(
        @CurrentMemberId Long adminId,
        @PathVariable String pollId,
        @RequestBody AdminPollTakedownRequest request
    ) {
        adminPollCommandService.takedown(adminId, pollId, request.status(), request.reason());

        return ResponseEntity.noContent().build();
    }

}
