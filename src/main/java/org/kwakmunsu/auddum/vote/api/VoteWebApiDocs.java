package org.kwakmunsu.auddum.vote.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.kwakmunsu.auddum.vote.api.dto.VoteCastRequest;
import org.kwakmunsu.auddum.vote.api.dto.VoteCastResponse;
import org.springframework.http.ResponseEntity;

/**
 * {@link VoteWebApi}의 Swagger 문서. 컨트롤러에는 동작만 두고 문서 어노테이션은 여기에 모은다.
 * 매핑과 검증 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "참여", description = "투표 참여")
public abstract class VoteWebApiDocs {

    @Operation(
        summary = "투표 참여",
        description = "로그인한 회원이면 회원으로, 아니면 익명 식별 쿠키(auddum_anon)로 참여한다. 쿠키가 없거나 위조되었으면 새로 발급해 Set-Cookie로 내린다. "
            + "참여하면 201이며 본문은 { myChoice, result }다. 이미 참여했으면 선택과 관계없이 409이고, 이때는 GET /polls/{pollId}로 내 선택과 결과를 확인한다. 선택을 바꾸는 API는 없다."
    )
    @ApiResponse(responseCode = "201", description = "참여함")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED, MALFORMED_REQUEST(공개 ID·본문 형식 오류), INVALID_OPTION")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 탈퇴했거나 없는 회원")
    @ApiResponse(responseCode = "403", description = "MEMBER_SUSPENDED")
    @ApiResponse(responseCode = "404", description = "POLL_NOT_FOUND: 없거나 숨김·삭제된 투표")
    @ApiResponse(responseCode = "409", description = "ALREADY_VOTED: 이미 참여함(선택과 관계없음), POLL_CLOSED: 마감됨")
    public abstract ResponseEntity<VoteCastResponse> castVote(
        @Parameter(description = "투표 공개 ID(12자 영숫자)", example = "aB3dE5fG7hJk") String pollId,
        @Parameter(hidden = true) Long memberId,
        @Parameter(hidden = true) String anonCookieValue,
        VoteCastRequest request,
        @Parameter(hidden = true) HttpServletResponse response
    );

}
