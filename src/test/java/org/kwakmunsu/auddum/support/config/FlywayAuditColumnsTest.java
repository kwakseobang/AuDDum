package org.kwakmunsu.auddum.support.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 모든 테이블이 BaseEntity의 created_at·updated_at을 NOT NULL로 가진다(V1 + V2). 기대값은 docs/ERD.md.
 */
@SpringBootTest
class FlywayAuditColumnsTest {

    private static final List<String> TABLES = List.of(
        "member", "member_auth", "refresh_token", "poll", "poll_option", "vote", "report", "admin_action");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 모든_테이블에_created_at과_updated_at이_NOT_NULL로_있다() {
        for (String table : TABLES) {
            var notNullAuditColumns = jdbc.queryForList(
                "SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_SCHEMA = SCHEMA() AND LOWER(TABLE_NAME) = ? "
                    + "AND LOWER(COLUMN_NAME) IN ('created_at', 'updated_at') AND IS_NULLABLE = 'NO'",
                String.class, table);

            assertThat(notNullAuditColumns).as(table).containsExactlyInAnyOrder("created_at", "updated_at");
        }
    }

}
