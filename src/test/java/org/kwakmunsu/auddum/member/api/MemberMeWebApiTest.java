package org.kwakmunsu.auddum.member.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.WebApiTestSupport;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * GET /api/v1/members/me 웹 계층(서비스는 목). API-명세 §2 GET /members/me, 시각 형식 규칙.
 * 가정한 이름: MemberQueryService#getMyInfo(Long memberId) -> Member (WebApiTestSupport에서 @MockitoBean).
 */
class MemberMeWebApiTest extends WebApiTestSupport {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 12, 13, 0, 0);

    @Test
    void 로그인한_회원이_조회하면_200과_status_role_createdAt만_내린다() {
        given(memberQueryService.getMyInfo(1L)).willReturn(createMember(MemberStatus.ACTIVE));

        mvcTester.get()
            .uri("/api/v1/members/me")
            .with(loginAs(1L))
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("ACTIVE"))
            .hasPathSatisfying("$.role", v -> v.assertThat().isEqualTo("MEMBER"))
            .hasPathSatisfying("$.createdAt", v -> v.assertThat().isEqualTo("2026-10-12T13:00:00+09:00"))
            .convertTo(Map.class)
            .satisfies(body -> assertThat(body.keySet()).containsExactlyInAnyOrder("status", "role", "createdAt"));
    }

    @Test
    void 정지_회원의_조회는_200이고_status는_SUSPENDED다() {
        given(memberQueryService.getMyInfo(1L)).willReturn(createMember(MemberStatus.SUSPENDED));

        mvcTester.get()
            .uri("/api/v1/members/me")
            .with(loginAs(1L))
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("SUSPENDED"));
    }

    @Test
    void 서비스가_401을_던지면_401_UNAUTHENTICATED다() {
        given(memberQueryService.getMyInfo(1L)).willThrow(new AuddumException(DefaultErrorCode.UNAUTHENTICATED));

        mvcTester.get()
            .uri("/api/v1/members/me")
            .with(loginAs(1L))
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("UNAUTHENTICATED"));
    }

    @Test
    void 로그인하지_않으면_401_UNAUTHENTICATED이고_서비스를_부르지_않는다() {
        mvcTester.get()
            .uri("/api/v1/members/me")
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("UNAUTHENTICATED"));

        verifyNoInteractions(memberQueryService);
    }

    private Member createMember(MemberStatus status) {
        var member = MemberFixture.createMember();
        ReflectionTestUtils.setField(member, "id", 1L);
        MemberFixture.changeStatus(member, status);
        ReflectionTestUtils.setField(member, "createdAt", CREATED_AT);

        return member;
    }

}
