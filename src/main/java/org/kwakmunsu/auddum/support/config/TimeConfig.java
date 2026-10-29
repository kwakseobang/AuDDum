package org.kwakmunsu.auddum.support.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 서버 시각의 기준을 KST로 고정한다. 감사(생성·수정 시각)는 이 Clock을 쓰고, 서비스는 {@link #KST}로 요청 시점 now를 한 번 정한다. */
@Configuration
public class TimeConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 응답 시각은 KST 오프셋이 붙은 ISO-8601이다. 저장값(LocalDateTime, KST)에 오프셋을 붙인다. */
    public static OffsetDateTime toKstOffset(LocalDateTime kst) {
        return kst == null ? null : kst.atZone(KST).toOffsetDateTime();
    }

    @Bean
    public Clock clock() {
        return Clock.system(KST);
    }

}
