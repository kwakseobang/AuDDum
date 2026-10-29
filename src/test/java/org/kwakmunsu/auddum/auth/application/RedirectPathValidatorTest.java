package org.kwakmunsu.auddum.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 돌아갈 경로 검증(brief 확정 사항, API-명세 SuccessHandler). 내부 경로만 통과하고 나머지는 기본 경로 "/"다.
 * 가정한 이름: RedirectPathValidator#resolve(String redirect) -> String (허용이면 그대로, 아니면 "/"), 기본 생성자.
 */
class RedirectPathValidatorTest {

    final RedirectPathValidator validator = new RedirectPathValidator();

    @ParameterizedTest
    @ValueSource(strings = {"/", "/polls", "/polls/AbC123xyz012", "/polls?category=SOME&size=20", "/polls#result"})
    void 슬래시로_시작하는_내부_경로는_그대로_허용한다(String path) {
        assertThat(validator.resolve(path)).isEqualTo(path);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 돌아갈_경로가_없으면_기본_경로로_보낸다(String path) {
        assertThat(validator.resolve(path)).isEqualTo("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.com", "http://evil.com/polls", "HTTPS://evil.com", "javascript:alert(1)", "mailto:a@b.c"})
    void 스킴이_있는_외부_주소는_기본_경로로_보낸다(String path) {
        assertThat(validator.resolve(path)).isEqualTo("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"//evil.com", "//", "///evil.com"})
    void 슬래시_두_개로_시작하면_기본_경로로_보낸다(String path) {
        assertThat(validator.resolve(path)).isEqualTo("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/\\evil.com", "\\evil.com", "/polls\\x"})
    void 역슬래시가_들어_있으면_기본_경로로_보낸다(String path) {
        assertThat(validator.resolve(path)).isEqualTo("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/polls\nSet-Cookie: a=b", "/polls\rx", "/polls\r\nx", "/po\tlls", "/polls\u0000", "/polls\u007f"})
    void 개행과_제어문자가_들어_있으면_기본_경로로_보낸다(String path) {
        assertThat(validator.resolve(path)).isEqualTo("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"polls", "evil.com", " /polls", "./polls", "../polls"})
    void 슬래시로_시작하지_않는_상대_경로는_기본_경로로_보낸다(String path) {
        assertThat(validator.resolve(path)).isEqualTo("/");
    }

}
