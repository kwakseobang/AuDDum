package org.kwakmunsu.auddum.auth.application;

import jakarta.servlet.http.HttpServletResponse;
import org.kwakmunsu.auddum.member.domain.Role;

/** 로그인에 성공한 회원에게 액세스·리프레시 토큰 쿠키를 발급한다. */
public interface TokenIssuer {

    void issue(Long memberId, Role role, HttpServletResponse response);

}
