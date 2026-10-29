package org.kwakmunsu.auddum.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.kwakmunsu.auddum.admin.domain.PollTakedown;

/**
 * 관리자 투표 조치 요청. 상태(필수)와 사유는 권한 확인(403)이 먼저라 서비스가 관리자 권한을 확인한 뒤 도메인(AdminAction)이 검증한다.
 * 허용되지 않은 상태 값은 본문 형식 오류(MALFORMED_REQUEST)다.
 */
@Schema(description = "관리자 투표 조치 요청")
public record AdminPollTakedownRequest(
    @Schema(description = "바꿀 투표 상태. HIDDEN(숨김, 복구 가능) 또는 DELETED(삭제, 복구 불가)", example = "HIDDEN")
    PollTakedown status,

    @Schema(description = "조치 사유. 필수, 200자 이하, 회원에게 노출하지 않는다", example = "특정인 비방 내용 확인")
    String reason
) {

}
