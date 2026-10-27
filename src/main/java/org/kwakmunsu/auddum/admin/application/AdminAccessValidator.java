package org.kwakmunsu.auddum.admin.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.stereotype.Component;

/**
 * 관리자 API 접근 검증. 관리자 서비스가 첫 단계에서 {@code validateAdmin}을 호출한다(컨트롤러가 아니다).
 * 권한이 없으면 시도를 WARN으로 남기고 403으로 거절한다. 거절 원인(일반·정지·탈퇴·없는 회원)은 응답에도 로그에도 구분하지 않고, 필요하면 memberId로 DB를 조회해 확인한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AdminAccessValidator {

    private final MemberReader memberReader;

    /** 관리자 권한이 없으면 403(FORBIDDEN)이다. 권한은 토큰이 아니라 DB로 매 요청 판단한다. */
    public void validateAdmin(Long memberId) {
        if (!memberReader.isAdmin(memberId)) {
            log.warn("[AdminAccessValidator] 관리자 권한 없는 접근 시도 - memberId={}", memberId);
            throw new AuddumException(DefaultErrorCode.FORBIDDEN);
        }
    }

}
