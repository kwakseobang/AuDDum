package org.kwakmunsu.auddum.member.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.kwakmunsu.auddum.member.domain.Member;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.support.config.TimeConfig;

/** 내 정보. 닉네임이나 프로필은 저장하지 않아 내려주지 않는다. */
@Schema(description = "내 정보")
public record MemberResponse(
    @Schema(description = "회원 상태. 탈퇴 회원은 조회할 수 없어 ACTIVE 또는 SUSPENDED다", example = "ACTIVE")
    MemberStatus status,

    @Schema(example = "MEMBER")
    Role role,

    @Schema(description = "가입 시각(KST 오프셋)", example = "2026-10-12T13:00:00+09:00")
    OffsetDateTime createdAt
) {

    public static MemberResponse of(Member member) {
        return new MemberResponse(
            member.getStatus(),
            member.getRole(),
            TimeConfig.toKstOffset(member.getCreatedAt())
        );
    }

}
