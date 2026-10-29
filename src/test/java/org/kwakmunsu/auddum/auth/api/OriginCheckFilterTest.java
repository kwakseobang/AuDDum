package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 인증-중복-참여 CSRF 대응, API-명세 FORBIDDEN: 쓰기 요청(GET·HEAD·OPTIONS 외)은 Origin이 허용된 프런트 도메인일 때만 받고,
 * 없거나 다르면 403 FORBIDDEN이다. 허용 주소는 하나(단일 생성자)이거나 여럿(목록 생성자)이고, 둘 다 같은 규칙으로 비교한다.
 */
class OriginCheckFilterTest {

    static final String FRONT = "https://auddum.example.com";
    static final String WWW = "https://www.auddum.example.com";

    static final OriginCheckFilter SINGLE = new OriginCheckFilter(FRONT);
    static final OriginCheckFilter LIST = new OriginCheckFilter(List.of(FRONT, WWW));

    // ---- 허용 ----

    @ParameterizedTest
    @MethodSource("허용되는_Origin")
    void 허용된_Origin의_쓰기_요청은_통과한다(OriginCheckFilter filter, String origin) throws Exception {
        var chain = passThrough(filter, "POST", origin);

        assertThat(chain.getRequest()).isNotNull();
    }

    static Stream<Arguments> 허용되는_Origin() {
        return Stream.of(
            Arguments.of(SINGLE, FRONT),
            Arguments.of(LIST, FRONT),
            Arguments.of(LIST, WWW)
        );
    }

    @Test
    void GET은_Origin이_없어도_통과한다() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        SINGLE.doFilter(new MockHttpServletRequest("GET", "/api/v1/polls"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ---- 거절 ----

    @ParameterizedTest
    @MethodSource("거절되는_쓰기_요청")
    void Origin이_없거나_허용_목록에_없는_쓰기_요청은_403_FORBIDDEN이다(OriginCheckFilter filter, String method, String origin) throws Exception {
        var request = new MockHttpServletRequest(method, "/api/v1/polls");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }

    static Stream<Arguments> 거절되는_쓰기_요청() {
        return Stream.of(
            Arguments.of(SINGLE, "POST", null),
            Arguments.of(SINGLE, "DELETE", "https://evil.example.com"),
            Arguments.of(LIST, "POST", null),
            Arguments.of(LIST, "POST", "https://evil.example.com")
        );
    }

    @Test
    void 거절_본문의_instance는_요청_경로다() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/polls/7/votes");
        request.addHeader("Origin", "https://evil.example.com");
        var response = new MockHttpServletResponse();

        SINGLE.doFilter(request, response, new MockFilterChain());

        assertThat(response.getContentAsString()).contains("\"instance\":\"/api/v1/polls/7/votes\"");
    }

    // ---- 비교 규칙: 소문자, 기본 포트, 경로 무시 ----

    @ParameterizedTest
    @MethodSource("정규화되어_같은_주소")
    void 허용_주소는_소문자_기본_포트_경로를_정규화해_브라우저가_보내는_Origin과_비교한다(String allowed, String origin) throws Exception {
        var chain = passThrough(new OriginCheckFilter(allowed), "POST", origin);

        assertThat(chain.getRequest()).isNotNull();
    }

    static Stream<Arguments> 정규화되어_같은_주소() {
        return Stream.of(
            Arguments.of("https://App.Example.com", "https://app.example.com"),
            Arguments.of("https://app.example.com:443", "https://app.example.com"),
            Arguments.of("https://app.example.com/some/path", "https://app.example.com")
        );
    }

    @Test
    void 목록의_각_항목도_소문자_기본_포트_경로를_정규화해_비교한다() throws Exception {
        var listFilter = new OriginCheckFilter(List.of("https://App.Example.com:443/path", "http://localhost:3000"));

        assertThat(passThrough(listFilter, "POST", "https://app.example.com").getRequest()).isNotNull();
        assertThat(passThrough(listFilter, "POST", "http://localhost:3000").getRequest()).isNotNull();
        assertThat(passThrough(listFilter, "POST", "http://localhost").getRequest()).isNull();
    }

    @Test
    void 비기본_포트는_포트까지_같은_Origin만_허용한다() throws Exception {
        var localFilter = new OriginCheckFilter("http://localhost:3000");

        assertThat(passThrough(localFilter, "POST", "http://localhost:3000").getRequest()).isNotNull();
        assertThat(passThrough(localFilter, "POST", "http://localhost").getRequest()).isNull();
        assertThat(passThrough(localFilter, "POST", "http://localhost:3001").getRequest()).isNull();
    }

    // ---- 설정 오류 ----

    @ParameterizedTest
    @MethodSource("만들_수_없는_허용_주소")
    void 스킴이나_호스트가_없는_허용_주소는_생성할_수_없다(ThrowingCallable creation) {
        assertThatThrownBy(creation).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<ThrowingCallable> 만들_수_없는_허용_주소() {
        return Stream.of(
            () -> new OriginCheckFilter("localhost:3000"),
            () -> new OriginCheckFilter("/just/a/path"),
            () -> new OriginCheckFilter(List.of("*"))
        );
    }

    private MockFilterChain passThrough(OriginCheckFilter target, String method, String origin) throws Exception {
        var request = new MockHttpServletRequest(method, "/api/v1/polls");
        request.addHeader("Origin", origin);
        var chain = new MockFilterChain();

        target.doFilter(request, new MockHttpServletResponse(), chain);

        return chain;
    }

}
