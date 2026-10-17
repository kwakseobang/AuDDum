package org.kwakmunsu.auddum.auth.application;

/**
 * 로그인 뒤 돌아갈 경로 검증. 서비스 내부 경로(`/`로 시작하는 상대 경로)만 허용하고, 그 밖의 값은 기본 경로(홈)로 바꾼다.
 * 외부 주소로 보내는 오픈 리다이렉트와 응답 헤더 주입을 막는다.
 */
public class RedirectPathValidator {

    static final String DEFAULT_PATH = "/";

    public String resolve(String redirect) {
        return isInternalPath(redirect) ? redirect : DEFAULT_PATH;
    }

    private boolean isInternalPath(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            return false;
        }

        return path.chars().noneMatch(c -> c == '\\' || Character.isISOControl(c));
    }

}
