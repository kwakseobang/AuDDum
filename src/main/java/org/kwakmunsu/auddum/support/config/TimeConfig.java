package org.kwakmunsu.auddum.support.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 서버 시각의 기준을 KST로 고정한다. 감사(생성·수정 시각)는 이 Clock을 쓰고, 서비스는 {@link #KST}로 요청 시점 now를 한 번 정한다. */
@Configuration
public class TimeConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(KST);
    }

}
