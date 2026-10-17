package org.kwakmunsu.auddum.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.kwakmunsu.auddum.member.api.dto.MemberResponse;
import org.springframework.http.ResponseEntity;

/**
 * {@link MemberWebApi}의 Swagger 문서. 매핑 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "회원", description = "내 정보 조회")
public abstract class MemberWebApiDocs {

    @Operation(
        summary = "내 정보 조회",
        description = "로그인한 회원의 상태·역할·가입 시각을 내린다. 정지 회원도 조회할 수 있다. 프런트엔드는 status로 화면을 다르게 보여주되 최종 검증은 서버가 한다."
    )
    @ApiResponse(responseCode = "200", description = "내 정보")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 로그인하지 않았거나 탈퇴한 회원")
    public abstract ResponseEntity<MemberResponse> getMyInfo(@Parameter(hidden = true) Long memberId);

}
