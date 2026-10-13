package org.kwakmunsu.auddum.poll.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.kwakmunsu.auddum.poll.api.dto.PollCreateRequest;
import org.springframework.http.ResponseEntity;

/**
 * {@link PollWebApi}의 Swagger 문서. 컨트롤러에는 동작만 두고 문서 어노테이션은 여기에 모은다.
 * 매핑과 검증 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "투표", description = "투표 생성·조회·삭제")
public abstract class PollWebApiDocs {

    @Operation(
        summary = "투표 생성",
        description = "투표와 선택지를 한 트랜잭션으로 만든다. 본문은 만든 투표의 공개 ID 문자열이고, 위치는 Location 헤더에도 담긴다.",
        parameters = @Parameter(
            name = "X-Member-Id", in = ParameterIn.HEADER, required = true,
            description = "임시 신원(인증 도입 #13 전까지). 회원 ID", example = "1")
    )
    @ApiResponse(responseCode = "201", description = "생성됨. 본문은 공개 ID")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED(errors에 위반 필드) 또는 MALFORMED_REQUEST")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED")
    @ApiResponse(responseCode = "409", description = "DUPLICATE_POLL: 직전과 같은 질문")
    @ApiResponse(responseCode = "429", description = "POLL_RATE_LIMITED: 직전 생성 후 30초 미만")
    public abstract ResponseEntity<String> createPoll(
        @Parameter(hidden = true) Long memberId,
        PollCreateRequest request
    );

}
