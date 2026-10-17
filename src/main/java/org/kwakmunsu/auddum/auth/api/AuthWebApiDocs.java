package org.kwakmunsu.auddum.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

/**
 * {@link AuthWebApi}의 Swagger 문서. 매핑 어노테이션은 컨트롤러가 가지고, 이 클래스의 메서드 시그니처와 맞춘다.
 */
@Tag(name = "인증", description = "토큰 재발급·로그아웃. 로그인 시작과 콜백은 프레임워크가 제공한다.")
public abstract class AuthWebApiDocs {

    @Operation(
        summary = "토큰 재발급",
        description = "리프레시 토큰 쿠키를 검증해 새 액세스·리프레시 토큰 쿠키를 내린다. 이전 리프레시 토큰은 폐기된다. "
            + "폐기된 토큰이 10초를 넘겨 다시 오면 재사용으로 보고 그 회원의 모든 기기를 로그아웃시킨다."
    )
    @ApiResponse(responseCode = "204", description = "재발급됨. 본문 없음, Set-Cookie로 전달")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 토큰이 없거나 만료·폐기됐거나 재사용으로 감지됨")
    public abstract ResponseEntity<Void> refresh(
        @Parameter(hidden = true) String refreshToken,
        @Parameter(hidden = true) HttpServletResponse response
    );

    @Operation(
        summary = "로그아웃",
        description = "현재 기기의 리프레시 토큰을 폐기하고 쿠키를 지운다. 다른 기기의 로그인은 유지된다. 이미 로그아웃된 상태여도 204다."
    )
    @ApiResponse(responseCode = "204", description = "로그아웃됨")
    public abstract ResponseEntity<Void> logout(
        @Parameter(hidden = true) String refreshToken,
        @Parameter(hidden = true) HttpServletResponse response
    );

}
