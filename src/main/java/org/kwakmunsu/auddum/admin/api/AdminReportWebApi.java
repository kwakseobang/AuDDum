package org.kwakmunsu.auddum.admin.api;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.admin.api.dto.AdminReportDetailResponse;
import org.kwakmunsu.auddum.admin.api.dto.AdminReportListRequest;
import org.kwakmunsu.auddum.admin.api.dto.AdminReportListResponse;
import org.kwakmunsu.auddum.admin.application.AdminReportQueryService;
import org.kwakmunsu.auddum.admin.application.dto.AdminReportSummary;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.CurrentMemberId;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 관리자 신고 API. 미로그인은 {@code @CurrentMemberId}가 401로 막고, 관리자 권한은 서비스가 확인한다. */
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@WebApi
public class AdminReportWebApi extends AdminReportWebApiDocs {

    private final AdminReportQueryService adminReportQueryService;

    @Override
    @GetMapping
    public ResponseEntity<AdminReportListResponse> getReports(
        @CurrentMemberId Long adminId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size
    ) {
        AdminReportListRequest request = new AdminReportListRequest(status, cursor, size);
        Slice<AdminReportSummary> slice = adminReportQueryService.getReports(
            adminId,
            request.reportStatus(),
            request.cursorId(),
            request.pageSize()
        );

        return ResponseEntity.ok(AdminReportListResponse.of(slice));
    }

    @Override
    @GetMapping("/{reportId}")
    public ResponseEntity<AdminReportDetailResponse> getReport(
        @CurrentMemberId Long adminId,
        @PathVariable Long reportId
    ) {
        return ResponseEntity.ok(AdminReportDetailResponse.from(adminReportQueryService.getReportDetail(adminId, reportId)));
    }

}
