package org.kwakmunsu.auddum.auth.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.application.MemberCommandService;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.CurrentMemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 탈퇴 응답은 인증 쿠키를 지워야 해서 쿠키를 다루는 auth.api에 둔다. */
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
@WebApi
public class MemberWithdrawWebApi extends MemberWithdrawWebApiDocs {

    private final MemberCommandService memberCommandService;
    private final AuthCookies authCookies;

    @Override
    @DeleteMapping
    public ResponseEntity<Void> withdraw(@CurrentMemberId Long memberId, HttpServletResponse response) {
        memberCommandService.withdraw(memberId);
        authCookies.clear(response);

        return ResponseEntity.noContent().build();
    }

}
