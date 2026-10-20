package org.kwakmunsu.auddum.vote.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.kwakmunsu.auddum.vote.api.dto.PollDetailResponse;
import org.kwakmunsu.auddum.vote.api.dto.VoteCastRequest;
import org.kwakmunsu.auddum.vote.api.dto.VoteCastResponse;
import org.springframework.http.ResponseEntity;

/**
 * {@link VoteWebApi}의 Swagger 문서. 컨트롤러에는 동작만 두고 문서 어노테이션은 여기에 모은다.
 * 매핑과 검증 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "참여", description = "투표 참여와 참여 정보가 붙는 투표 상세 조회")
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

    @Tag(name = "투표")
    @Operation(
        summary = "투표 상세",
        description = "선택지·마감·참여 수·노출 상태(status)와 내 선택(myChoice), 결과(result)를 내린다. "
            + "result는 마감되었거나 내가 참여했을 때만 내려가고 아니면 null이다. 참여 여부는 회원 ID 또는 익명 쿠키로 판단하며 쿠키는 읽기만 한다. "
            + "숨김·삭제 투표는 작성자와 관리자(DB의 role이 ADMIN)에게만 200이고 그 밖에는 404다."
    )
    @ApiResponse(responseCode = "200", description = "상세")
    @ApiResponse(responseCode = "400", description = "MALFORMED_REQUEST: 공개 ID 형식 오류")
    @ApiResponse(responseCode = "404", description = "POLL_NOT_FOUND: 없거나 작성자·관리자가 볼 수 없는 숨김·삭제된 투표")
    public abstract ResponseEntity<PollDetailResponse> getPoll(
        @Parameter(description = "투표 공개 ID(12자 영숫자)", example = "aB3dE5fG7hJk") String pollId,
        @Parameter(hidden = true) Long memberId,
        @Parameter(hidden = true) String anonCookieValue
    );

}
