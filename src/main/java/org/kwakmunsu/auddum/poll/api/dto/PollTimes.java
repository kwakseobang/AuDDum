package org.kwakmunsu.auddum.poll.api.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.kwakmunsu.auddum.support.config.TimeConfig;

/** 응답 시각은 KST 오프셋이 붙은 ISO-8601이다(API 명세 §1). 저장값(LocalDateTime, KST)에 오프셋을 붙인다. */
final class PollTimes {

    private PollTimes() {
    }

    static OffsetDateTime toKstOffset(LocalDateTime kst) {
        return kst == null ? null : kst.atZone(TimeConfig.KST).toOffsetDateTime();
    }

}
