package org.kwakmunsu.auddum.report.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.report.application.dto.ReporterCredential;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.kwakmunsu.auddum.support.anon.AnonIdentity;
import org.kwakmunsu.auddum.support.anon.AnonIdentityResolver;
import org.springframework.stereotype.Component;

/**
 * 신고 요청의 신고자를 정한다. 로그인한 회원이면 정상 회원인지 확인하고(정지 403, 탈퇴·없음 401) 회원 ID를 쓰며 익명 쿠키는 보지 않는다.
 * 비회원이면 쿠키 해석(유효하면 그 UUID, 없거나 위조면 발급)을 {@link AnonIdentityResolver}에 시키고 결과를 {@link Reporter}로 감싼다.
 */
@RequiredArgsConstructor
@Component
public class ReporterResolver {

    private final AnonIdentityResolver anonIdentityResolver;
    private final MemberReader memberReader;

    public ResolvedReporter resolve(ReporterCredential credential) {
        if (credential.isMember()) {
            memberReader.getActiveMember(credential.memberId());

            return ResolvedReporter.existing(Reporter.member(credential.memberId()));
        }

        AnonIdentity identity = anonIdentityResolver.resolve(credential.anonCookieValue());

        return new ResolvedReporter(Reporter.anonymous(identity.id()), identity.newCookieValue());
    }

}
