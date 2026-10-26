package org.kwakmunsu.auddum.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.kwakmunsu.auddum.report.api.dto.ReportSubmitRequest;
import org.springframework.http.ResponseEntity;

/**
 * {@link ReportWebApi}의 Swagger 문서. 컨트롤러에는 동작만 두고 문서 어노테이션은 여기에 모은다.
 * 매핑과 검증 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "신고", description = "투표 신고")
public abstract class ReportWebApiDocs {

    @Operation(
        summary = "투표 신고",
        description = "로그인한 회원이면 회원으로, 아니면 익명 식별 쿠키(auddum_anon)로 신고한다. 쿠키가 없거나 위조되었으면 새로 발급해 Set-Cookie로 내린다. "
            + "접수하면 201이며 본문은 없다. 신고는 투표당 신고자당 1회이고, 신고해도 투표의 노출 상태는 바뀌지 않는다. 마감된 투표도 신고할 수 있다."
    )
    @ApiResponse(responseCode = "201", description = "접수함")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED(사유 누락·ETC 상세 누락·길이 초과), MALFORMED_REQUEST(공개 ID·본문 형식 오류·허용되지 않은 사유)")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 탈퇴했거나 없는 회원")
    @ApiResponse(responseCode = "403", description = "MEMBER_SUSPENDED")
    @ApiResponse(responseCode = "404", description = "POLL_NOT_FOUND: 없거나 숨김·삭제된 투표")
    @ApiResponse(responseCode = "409", description = "REPORT_ALREADY_SUBMITTED: 이미 신고함")
    public abstract ResponseEntity<Void> submitReport(
        @Parameter(description = "투표 공개 ID(12자 영숫자)", example = "aB3dE5fG7hJk") String pollId,
        @Parameter(hidden = true) Long memberId,
        @Parameter(hidden = true) String anonCookieValue,
        ReportSubmitRequest request,
        @Parameter(hidden = true) HttpServletResponse response
    );

}
