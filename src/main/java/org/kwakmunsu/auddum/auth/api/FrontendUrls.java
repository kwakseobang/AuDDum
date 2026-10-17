package org.kwakmunsu.auddum.auth.api;

/** 프런트 주소 설정을 다룬다. 끝의 슬래시를 없애 경로를 붙여도 `//`가 생기지 않게 한다. */
final class FrontendUrls {

    private FrontendUrls() {
    }

    static String normalize(String baseUrl) {
        int end = baseUrl.length();
        while (end > 0 && baseUrl.charAt(end - 1) == '/') {
            end--;
        }

        return baseUrl.substring(0, end);
    }

}
