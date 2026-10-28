package org.kwakmunsu.auddum.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.kwakmunsu.auddum.admin.api.dto.AdminPollTakedownRequest;
import org.springframework.http.ResponseEntity;

/**
 * {@link AdminPollWebApi}의 Swagger 문서. 컨트롤러에는 동작만 두고 문서 어노테이션은 여기에 모은다.
 * 매핑과 검증 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "관리자 투표", description = "관리자의 투표 숨김·삭제. 관리자 권한(DB의 role이 ADMIN이고 정상 상태)이 필요하다.")
public abstract class AdminPollWebApiDocs {

    @Operation(
        summary = "투표 숨김·삭제",
        description = "투표를 숨기거나(HIDDEN) 삭제하고(DELETED) 그 투표의 대기 신고를 모두 조치 완료로 종료한다. 상태 변경, 신고 종료, 이력 기록은 한 트랜잭션이다. 숨김은 공개 투표만 가능하고 복구할 수 있다. 삭제는 공개·숨김 투표에서 가능하고 복구할 수 없다."
    )
    @ApiResponse(responseCode = "204", description = "조치됨")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: 상태 누락·사유 규칙 위반, MALFORMED_REQUEST: 투표 ID 형식 오류·본문 형식 오류·허용되지 않은 상태 값")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: 관리자가 아님(정지·탈퇴한 관리자 포함)")
    @ApiResponse(responseCode = "404", description = "POLL_NOT_FOUND: 없거나 삭제된 투표")
    @ApiResponse(responseCode = "409", description = "POLL_NOT_PUBLIC: 이미 숨겨진 투표를 숨기려 했다")
    public abstract ResponseEntity<Void> takedownPoll(
        @Parameter(hidden = true) Long adminId,
        @Parameter(description = "투표 공개 ID(12자 영숫자)", example = "aB3dE5fG7hJk") String pollId,
        AdminPollTakedownRequest request
    );

}
