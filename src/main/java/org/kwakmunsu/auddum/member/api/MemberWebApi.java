package org.kwakmunsu.auddum.member.api;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.api.dto.MemberResponse;
import org.kwakmunsu.auddum.member.application.MemberQueryService;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.CurrentMemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@WebApi
public class MemberWebApi extends MemberWebApiDocs {

    private final MemberQueryService memberQueryService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<MemberResponse> getMyInfo(@CurrentMemberId Long memberId) {
        return ResponseEntity.ok(MemberResponse.of(memberQueryService.getMyInfo(memberId)));
    }

}
