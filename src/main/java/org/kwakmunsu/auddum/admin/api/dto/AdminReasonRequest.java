package org.kwakmunsu.auddum.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 조치 요청. 사유는 서비스가 관리자 권한을 확인한 뒤 도메인(AdminAction)이 검증한다(필수, 공백뿐이면 없는 것, 200자 이하). */
@Schema(description = "관리자 조치 요청")
public record AdminReasonRequest(
    @Schema(description = "조치 사유. 필수, 200자 이하, 회원에게 노출하지 않는다", example = "특정인 비방 내용 확인")
    String reason
) {

}
