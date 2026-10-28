package org.kwakmunsu.auddum.admin.api;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.admin.api.dto.AdminReasonRequest;
import org.kwakmunsu.auddum.admin.application.AdminMemberCommandService;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.CurrentMemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 관리자 회원 API. 미로그인은 {@code @CurrentMemberId}가 401로 막고, 관리자 권한은 서비스가 확인한다. */
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
@WebApi
public class AdminMemberWebApi extends AdminMemberWebApiDocs {

    private final AdminMemberCommandService adminMemberCommandService;

    @Override
    @PostMapping("/{memberId}/suspend")
    public ResponseEntity<Void> suspendMember(
        @CurrentMemberId Long adminId,
        @PathVariable Long memberId,
        @RequestBody AdminReasonRequest request
    ) {
        adminMemberCommandService.suspend(adminId, memberId, request.reason());

        return ResponseEntity.noContent().build();
    }

}
