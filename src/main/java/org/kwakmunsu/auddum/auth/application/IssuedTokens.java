package org.kwakmunsu.auddum.auth.application;

/** 새로 발급한 토큰 원문. 쿠키에 담아 내려보낼 뿐 저장하지 않는다(리프레시 토큰은 해시만 저장). */
public record IssuedTokens(String accessToken, String refreshToken) {

}
