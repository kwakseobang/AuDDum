package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/**
 * CORS는 프런트 origin 목록만 허용하고 자격 증명(쿠키)을 허용하며 와일드카드는 쓰지 않는다.
 * 쓰기 요청의 Origin 검사도 같은 목록을 쓴다(CSRF, FORBIDDEN).
 * 결정(사용자 승인): 허용 Origin = {@code auddum.frontend.base-url}(단일, 필수)의 origin + {@code auddum.cors.allowed-origins}
 * (쉼표 구분, 선택)의 항목들. 항상 합집합이다. 이 클래스는 둘 다 채운 경우(합집합)를 본다.
 */
@TestPropertySource(properties = {
    "auddum.frontend.base-url=https://auddum.example.com",
    "auddum.cors.allowed-origins=https://www.auddum.example.com"
})
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class CorsAllowlistChainTest extends IntegrationTestSupport {

    static final String ROOT_ORIGIN = "https://auddum.example.com";
    static final String WWW_ORIGIN = "https://www.auddum.example.com";
    static final String EVIL_ORIGIN = "https://evil.example.com";
    static final String REQUEST_BODY = """
        {"question": "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?",
         "category": "SOME",
         "options": ["관심 없음", "그냥 바쁜 것"]}
        """;

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;
    final MemberJpaRepository memberJpaRepository;

    @Test
    void base_url과_추가_목록의_Origin_모두_사전_요청에_자격_증명_허용과_해당_Origin을_돌려준다() {
        for (String origin : new String[] {ROOT_ORIGIN, WWW_ORIGIN}) {
            var result = preflight(origin);

            assertThat(result).hasStatusOk();
            assertThat(result).headers().hasValue("Access-Control-Allow-Origin", origin);
            assertThat(result).headers().hasValue("Access-Control-Allow-Credentials", "true");
        }
    }

    @Test
    void 사전_요청_응답은_와일드카드를_쓰지_않는다() {
        var result = preflight(ROOT_ORIGIN);

        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNotEqualTo("*");
    }

    @Test
    void 목록_밖_Origin의_사전_요청은_허용_헤더를_받지_못한다() {
        var result = preflight(EVIL_ORIGIN);

        assertThat(result).hasStatus(403);
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials")).isNull();
    }

    @Test
    void 목록의_두번째_Origin으로도_쓰기_요청이_통과한다() {
        var memberId = memberJpaRepository.save(Member.signUp()).getId();

        var result = mvcTester.post().uri("/api/v1/polls")
            .header("Origin", WWW_ORIGIN)
            .cookie(accessCookie(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY)
            .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).headers().hasValue("Access-Control-Allow-Origin", WWW_ORIGIN);
        assertThat(result).headers().hasValue("Access-Control-Allow-Credentials", "true");
    }

    @Test
    void 목록_밖_Origin의_쓰기_요청은_403_FORBIDDEN이다() {
        var memberId = memberJpaRepository.save(Member.signUp()).getId();

        var result = mvcTester.post().uri("/api/v1/polls")
            .header("Origin", EVIL_ORIGIN)
            .cookie(accessCookie(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY)
            .exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void 목록이_여럿이어도_Origin_없는_쓰기_요청은_403_FORBIDDEN이다() {
        var memberId = memberJpaRepository.save(Member.signUp()).getId();

        var result = mvcTester.post().uri("/api/v1/polls")
            .cookie(accessCookie(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY)
            .exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult preflight(String origin) {
        return mvcTester.options().uri("/api/v1/polls")
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type")
            .exchange();
    }

    private Cookie accessCookie(Long memberId) {
        LocalDateTime now = LocalDateTime.now(TimeConfig.KST);

        return new Cookie(AuthCookies.ACCESS_COOKIE, jwtTokenProvider.createAccessToken(memberId, Role.MEMBER, now));
    }

}
