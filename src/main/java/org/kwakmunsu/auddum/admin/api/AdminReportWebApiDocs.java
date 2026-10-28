package org.kwakmunsu.auddum.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.kwakmunsu.auddum.admin.api.dto.AdminReasonRequest;
import org.kwakmunsu.auddum.admin.api.dto.AdminReportDetailResponse;
import org.kwakmunsu.auddum.admin.api.dto.AdminReportListResponse;
import org.springframework.http.ResponseEntity;

/**
 * {@link AdminReportWebApi}의 Swagger 문서. 컨트롤러에는 동작만 두고 문서 어노테이션은 여기에 모은다.
 * 매핑과 검증 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "관리자 신고", description = "관리자의 신고 조회·처리. 관리자 권한(DB의 role이 ADMIN이고 정상 상태)이 필요하다.")
public abstract class AdminReportWebApiDocs {

    @Operation(
        summary = "신고 목록",
        description = "상태별(기본 PENDING) 신고를 접수 순(id 오름차순)으로 내린다. 각 항목에 같은 투표에 쌓인 신고 수(pollReportCount)가 있다. "
            + "cursor는 이전 응답의 nextCursor를 그대로 넘기는 불투명한 값이다."
    )
    @ApiResponse(responseCode = "200", description = "목록. 신고가 없으면 빈 items")
    @ApiResponse(responseCode = "400", description = "MALFORMED_REQUEST: status가 PENDING·RESOLVED·DISMISSED가 아님, cursor 형식 오류, size가 숫자가 아님")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: 관리자가 아님(정지·탈퇴한 관리자 포함)")
    public abstract ResponseEntity<AdminReportListResponse> getReports(
        @Parameter(hidden = true) Long adminId,
        @Parameter(description = "신고 상태. 기본 PENDING", example = "PENDING") String status,
        @Parameter(description = "이전 응답의 nextCursor. 없으면 첫 페이지") String cursor,
        @Parameter(description = "한 페이지 개수. 기본 20, 1보다 작으면 1, 50을 넘으면 50으로 보정") Integer size
    );

    @Operation(
        summary = "신고 상세",
        description = "신고 내용과 대상 투표(숨김·삭제여도), 같은 투표의 다른 신고, 조치 이력을 내린다. 신고자는 노출하지 않는다."
    )
    @ApiResponse(responseCode = "200", description = "상세")
    @ApiResponse(responseCode = "400", description = "MALFORMED_REQUEST: 신고 ID가 숫자가 아님")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: 관리자가 아님(정지·탈퇴한 관리자 포함)")
    @ApiResponse(responseCode = "404", description = "REPORT_NOT_FOUND")
    public abstract ResponseEntity<AdminReportDetailResponse> getReport(
        @Parameter(hidden = true) Long adminId,
        @Parameter(description = "신고 내부 id", example = "17") Long reportId
    );

    @Operation(
        summary = "신고 기각",
        description = "신고를 기각하고 같은 투표의 다른 대기 신고도 함께 기각한다. 투표와 회원의 상태는 바꾸지 않는다. 신고 상태 변경과 이력 기록은 한 트랜잭션이다."
    )
    @ApiResponse(responseCode = "204", description = "기각됨")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: 사유 규칙 위반, MALFORMED_REQUEST: 신고 ID 형식 오류·본문 형식 오류")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: 관리자가 아님(정지·탈퇴한 관리자 포함)")
    @ApiResponse(responseCode = "404", description = "REPORT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "REPORT_ALREADY_HANDLED: 이미 처리됐거나 동시 처리에서 졌다")
    public abstract ResponseEntity<Void> dismissReport(
        @Parameter(hidden = true) Long adminId,
        @Parameter(description = "신고 내부 id", example = "17") Long reportId,
        AdminReasonRequest request
    );

}
