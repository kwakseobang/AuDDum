package org.kwakmunsu.auddum.poll.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.kwakmunsu.auddum.poll.api.dto.PollCreateRequest;
import org.kwakmunsu.auddum.poll.api.dto.PollListResponse;
import org.springframework.http.ResponseEntity;

/**
 * {@link PollWebApi}의 Swagger 문서. 컨트롤러에는 동작만 두고 문서 어노테이션은 여기에 모은다.
 * 매핑과 검증 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "투표", description = "투표 생성·조회·삭제")
public abstract class PollWebApiDocs {

    @Operation(
        summary = "투표 생성",
        description = "투표와 선택지를 한 트랜잭션으로 만든다. 본문은 만든 투표의 공개 ID 문자열이고, 위치는 Location 헤더에도 담긴다. 로그인(액세스 토큰 쿠키)이 필요하다."
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

    @Operation(
        summary = "투표 목록",
        description = "공개(PUBLIC) 투표를 최신순으로 내린다. 마감된 투표도 closed=true로 포함하고, 각 투표의 전체 참여 수(participantCount)를 담는다. "
            + "cursor는 이전 응답의 nextCursor를 그대로 넘기는 불투명한 값이다."
    )
    @ApiResponse(responseCode = "200", description = "목록. 투표가 없으면 빈 items")
    @ApiResponse(responseCode = "400", description = "MALFORMED_REQUEST: cursor 형식 오류, size가 숫자가 아님")
    public abstract ResponseEntity<PollListResponse> getPolls(
        @Parameter(description = "이전 응답의 nextCursor. 없으면 첫 페이지") String cursor,
        @Parameter(description = "한 페이지 개수. 기본 20, 1보다 작으면 1, 50을 넘으면 50으로 보정") Integer size
    );

}
