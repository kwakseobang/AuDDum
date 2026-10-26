package org.kwakmunsu.auddum.support.anon;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider.Verification;
import org.springframework.stereotype.Component;

/**
 * 익명 쿠키 값으로 비회원의 식별자를 정한다(인증-중복-참여 §2). 유효한 쿠키면 그 UUID를 쓰고, 쿠키가 없거나 위조된 경우에는 새로 발급한다.
 * 위조는 오류 없이 처리하고 WARN으로 남긴다. 위조된 값은 외부 입력이라 로그에 넣지 않고 사유만 남긴다.
 * 회원 여부와 회원 상태는 보지 않는다. 그 판단은 이 클래스를 쓰는 각 도메인의 리졸버가 먼저 한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AnonIdentityResolver {

    private final AnonCookieProvider anonCookieProvider;

    /** 쓰기용. 쿠키가 없거나(빈 문자열 포함) 위조되었으면 새로 발급한다. */
    public AnonIdentity resolve(String cookieValue) {
        if (cookieValue == null || cookieValue.isEmpty()) {
            return issueNew();
        }

        return switch (anonCookieProvider.check(cookieValue)) {
            case Verification.Valid valid -> AnonIdentity.existing(valid.id());
            case Verification.Malformed _ -> forged("형식 오류");
            case Verification.SignatureMismatch _ -> forged("서명 불일치");
        };
    }

    /**
     * 조회용. 쿠키를 읽기만 하고 발급하지 않으며, 쿠키가 없거나 위조된 경우는 식별자가 없는 것으로 본다.
     * 위조 로그도 남기지 않는다(조회는 호출이 많다).
     */
    public Optional<UUID> find(String cookieValue) {
        if (cookieValue == null || cookieValue.isEmpty()) {
            return Optional.empty();
        }

        return switch (anonCookieProvider.check(cookieValue)) {
            case Verification.Valid valid -> Optional.of(valid.id());
            case Verification.Malformed _, Verification.SignatureMismatch _ -> Optional.empty();
        };
    }

    private AnonIdentity forged(String reason) {
        log.warn("[AnonIdentityResolver] 위조 의심 익명 쿠키 무효 처리 후 재발급 - reason={}", reason);

        return issueNew();
    }

    private AnonIdentity issueNew() {
        return AnonIdentity.issued(anonCookieProvider.issue());
    }

}
