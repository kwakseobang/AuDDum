package org.kwakmunsu.auddum.vote;

import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 실제 MySQL이 필요한 동시성 테스트의 공통 설정(테스트-배포 §1 "실제 MySQL로 한 번 더 확인").
 * Testcontainers의 MySQL 8.4 컨테이너를 static으로 띄워 클래스 간에 재사용하고, {@code @ServiceConnection}이
 * datasource 접속 값을 컨테이너로 덮어쓴다. 스키마는 Flyway가 만든다. 이 테스트는 @Transactional을 쓰지 않고
 * 실제로 커밋하므로 @AfterEach에서 지운다.
 */
@Testcontainers
public abstract class MySqlConcurrencyTestSupport extends VoteIntegrationTestSupport {

    /** 동시에 던지는 요청 수. */
    protected static final int THREADS = 10;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @DynamicPropertySource
    static void connectionPool(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> String.valueOf(THREADS * 2));
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET time_zone = '+09:00'");
    }

    @AfterEach
    void cleanUp() {
        voteJpaRepository.deleteAll();
        pollJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

}
