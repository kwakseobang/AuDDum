package org.kwakmunsu.auddum.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 인증-중복-참여 §1 CSRF 대응, API-명세 §7 FORBIDDEN: 쓰기 요청(GET·HEAD·OPTIONS 외)은 Origin이 허용된 프런트 도메인일 때만 받고,
 * 없거나 다르면 403 FORBIDDEN이다.
 */
class OriginCheckFilterTest {

    static final String FRONT = "https://auddum.example.com";

    final OriginCheckFilter filter = new OriginCheckFilter(FRONT);

    @Test
    void 허용된_Origin의_쓰기_요청은_통과한다() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/polls");
        request.addHeader("Origin", FRONT);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void Origin이_없는_쓰기_요청은_403_FORBIDDEN이다() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/polls"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void 다른_Origin의_쓰기_요청은_403_FORBIDDEN이다() throws Exception {
        var request = new MockHttpServletRequest("DELETE", "/api/v1/polls/1");
        request.addHeader("Origin", "https://evil.example.com");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void GET은_Origin이_없어도_통과한다() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/polls"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 프런트_주소가_대문자_호스트여도_브라우저가_보내는_소문자_Origin을_허용한다() throws Exception {
        var upperFilter = new OriginCheckFilter("https://App.Example.com");

        var chain = passThrough(upperFilter, "POST", "https://app.example.com");

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 프런트_주소에_기본_포트가_명시돼도_포트_없는_Origin을_허용한다() throws Exception {
        var portFilter = new OriginCheckFilter("https://app.example.com:443");

        var chain = passThrough(portFilter, "POST", "https://app.example.com");

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 프런트_주소에_경로가_붙어_있어도_스킴_호스트_포트만_비교한다() throws Exception {
        var pathFilter = new OriginCheckFilter("https://app.example.com/some/path");

        var chain = passThrough(pathFilter, "POST", "https://app.example.com");

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 비기본_포트는_포트까지_같은_Origin만_허용한다() throws Exception {
        var localFilter = new OriginCheckFilter("http://localhost:3000");

        assertThat(passThrough(localFilter, "POST", "http://localhost:3000").getRequest()).isNotNull();
        assertThat(passThrough(localFilter, "POST", "http://localhost").getRequest()).isNull();
        assertThat(passThrough(localFilter, "POST", "http://localhost:3001").getRequest()).isNull();
    }

    @Test
    void 스킴이나_호스트가_없는_프런트_주소는_생성할_수_없다() {
        assertThatThrownBy(() -> new OriginCheckFilter("localhost:3000")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OriginCheckFilter("/just/a/path")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 거절_본문의_instance는_요청_경로다() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/polls/7/votes");
        request.addHeader("Origin", "https://evil.example.com");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getContentAsString()).contains("\"instance\":\"/api/v1/polls/7/votes\"");
    }

    @Test
    void 허용_목록에_있는_Origin은_모두_통과한다() throws Exception {
        var listFilter = new OriginCheckFilter(List.of("https://auddum.example.com", "https://www.auddum.example.com"));

        assertThat(passThrough(listFilter, "POST", "https://auddum.example.com").getRequest()).isNotNull();
        assertThat(passThrough(listFilter, "POST", "https://www.auddum.example.com").getRequest()).isNotNull();
    }

    @Test
    void 허용_목록에_없는_Origin의_쓰기_요청은_403_FORBIDDEN이다() throws Exception {
        var listFilter = new OriginCheckFilter(List.of("https://auddum.example.com", "https://www.auddum.example.com"));
        var request = new MockHttpServletRequest("POST", "/api/v1/polls");
        request.addHeader("Origin", "https://evil.example.com");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        listFilter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void 목록이_여럿이어도_Origin이_없는_쓰기_요청은_403이다() throws Exception {
        var listFilter = new OriginCheckFilter(List.of("https://auddum.example.com", "https://www.auddum.example.com"));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        listFilter.doFilter(new MockHttpServletRequest("POST", "/api/v1/polls"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void 목록의_각_항목도_소문자_기본_포트_경로를_정규화해_비교한다() throws Exception {
        var listFilter = new OriginCheckFilter(List.of("https://App.Example.com:443/path", "http://localhost:3000"));

        assertThat(passThrough(listFilter, "POST", "https://app.example.com").getRequest()).isNotNull();
        assertThat(passThrough(listFilter, "POST", "http://localhost:3000").getRequest()).isNotNull();
        assertThat(passThrough(listFilter, "POST", "http://localhost").getRequest()).isNull();
    }

    @Test
    void 와일드카드_항목은_스킴과_호스트가_없어_생성할_수_없다() {
        assertThatThrownBy(() -> new OriginCheckFilter(List.of("*")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private MockFilterChain passThrough(OriginCheckFilter target, String method, String origin) throws Exception {
        var request = new MockHttpServletRequest(method, "/api/v1/polls");
        request.addHeader("Origin", origin);
        var chain = new MockFilterChain();

        target.doFilter(request, new MockHttpServletResponse(), chain);

        return chain;
    }

}
