package org.kwakmunsu.auddum.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

/**
 * {@link MemberWithdrawWebApi}의 Swagger 문서. 매핑 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "회원", description = "탈퇴")
public abstract class MemberWithdrawWebApiDocs {

    @Operation(
        summary = "회원 탈퇴",
        description = "회원을 탈퇴 처리하고 소셜 연동 정보와 리프레시 토큰을 삭제한 뒤 쿠키를 지운다. 정지 중에도 할 수 있다. 되돌릴 수 없고, 같은 소셜 계정으로 다시 가입하면 새 회원이다."
    )
    @ApiResponse(responseCode = "204", description = "탈퇴됨. 액세스·리프레시 쿠키 삭제")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 로그인하지 않았거나 이미 탈퇴한 회원")
    public abstract ResponseEntity<Void> withdraw(
        @Parameter(hidden = true) Long memberId,
        @Parameter(hidden = true) HttpServletResponse response
    );

}
