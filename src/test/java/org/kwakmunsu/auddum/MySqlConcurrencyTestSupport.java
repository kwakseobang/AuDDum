package org.kwakmunsu.auddum;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 실제 MySQL이 필요한 동시성 테스트의 공통 설정(테스트-배포 §1 "실제 MySQL로 한 번 더 확인").
 * Testcontainers의 MySQL 8.4 컨테이너를 JVM당 하나만 띄워 모든 하위 클래스가 함께 쓰고(종료는 Ryuk), {@code @ServiceConnection}이
 * datasource 접속 값을 컨테이너로 덮어쓴다. 스키마는 Flyway가 만든다. 이 테스트는 @Transactional을 쓰지 않고
 * 실제로 커밋하므로 @AfterEach에서 지운다(참조하는 report·vote 기록을 먼저 지운다).
 * <p>
 * {@code @Container}로 두면 하위 클래스마다 컨테이너를 새로 띄우고 내리는데, 같은 설정의 Spring 컨텍스트는 캐시되어
 * 이미 내려간 첫 컨테이너로 접속하려 하므로 쓰지 않는다.
 */
public abstract class MySqlConcurrencyTestSupport extends DataIntegrationTestSupport {

    /** 동시에 던지는 요청 수. */
    protected static final int THREADS = 10;

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void connectionPool(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> String.valueOf(THREADS * 2));
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET time_zone = '+09:00'");
    }

    @Autowired
    private JdbcTemplate cleanUpJdbc;

    @AfterEach
    void cleanUp() {
        cleanUpJdbc.update("DELETE FROM report");
        cleanUpJdbc.update("DELETE FROM vote");
        pollJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

}
