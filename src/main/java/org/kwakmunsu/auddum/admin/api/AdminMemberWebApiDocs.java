package org.kwakmunsu.auddum.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.kwakmunsu.auddum.admin.api.dto.AdminReasonRequest;
import org.springframework.http.ResponseEntity;

/**
 * {@link AdminMemberWebApi}의 Swagger 문서. 컨트롤러에는 동작만 두고 문서 어노테이션은 여기에 모은다.
 * 매핑과 검증 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "관리자 회원", description = "관리자의 회원 정지. 관리자 권한(DB의 role이 ADMIN이고 정상 상태)이 필요하다.")
public abstract class AdminMemberWebApiDocs {

    @Operation(
        summary = "회원 정지",
        description = "정상 회원을 정지한다. 기간 없이 해제할 때까지 유지되고, 그 회원의 기존 투표와 신고 상태는 바꾸지 않는다."
    )
    @ApiResponse(responseCode = "204", description = "정지됨")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: 사유 규칙 위반, MALFORMED_REQUEST: 회원 ID 형식 오류·본문 형식 오류")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: 관리자가 아님(정지·탈퇴한 관리자 포함)")
    @ApiResponse(responseCode = "404", description = "MEMBER_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "MEMBER_NOT_ACTIVE: 이미 정지·탈퇴한 회원")
    public abstract ResponseEntity<Void> suspendMember(
        @Parameter(hidden = true) Long adminId,
        @Parameter(description = "회원 내부 id", example = "5") Long memberId,
        AdminReasonRequest request
    );

}
