package org.kwakmunsu.auddum.support.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway V1 스키마 검증. 기대값은 docs/ERD.md §4·§5, docs/테스트-배포.md §5에서 가져왔다.
 * 테스트 H2가 같은 마이그레이션 SQL로 스키마를 만든다는 전제다.
 */
@SpringBootTest
@Transactional
class FlywayV1SchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    private int seq = 0;

    // ---------- 적용 여부 ----------

    @Test
    void Flyway_V1_마이그레이션이_성공적으로_적용된다() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void 필수_테이블이_존재한다() {
        List<String> tables = jdbc.queryForList(
                "SELECT LOWER(TABLE_NAME) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_TYPE = 'BASE TABLE' "
                        + "AND LOWER(TABLE_NAME) <> 'flyway_schema_history'",
                String.class);

        assertThat(tables).contains(
                "member", "member_auth", "refresh_token", "poll",
                "poll_option", "vote", "report", "admin_action");
    }

    // ---------- 제약용 인덱스 (ERD §5). 조회 최적화 인덱스는 V1에 없다 ----------

    @Test
    void 제약용_인덱스가_있다() {
        assertThat(hasIndex("vote", "poll_id", "option_id")).isTrue();
        assertThat(hasIndex("refresh_token", "member_id")).isTrue();
        assertThat(hasIndex("admin_action", "target_poll_id")).isTrue();
        assertThat(hasIndex("admin_action", "target_member_id")).isTrue();
    }

    // ---------- 기본값 (ERD §4) ----------

    @Test
    void member_role_기본값은_MEMBER다() {
        long id = insert("INSERT INTO member (status, created_at, updated_at) VALUES ('ACTIVE', NOW(), NOW())");

        assertThat(jdbc.queryForObject("SELECT role FROM member WHERE id = ?", String.class, id))
                .isEqualTo("MEMBER");
    }

    @Test
    void poll_status_기본값은_PUBLIC이다() {
        long author = member();
        long id = insert("INSERT INTO poll (author_id, question, category, created_at, updated_at, public_id) "
                + "VALUES (" + author + ", 'q', 'SOME', NOW(), NOW(), '" + publicId() + "')");

        assertThat(jdbc.queryForObject("SELECT status FROM poll WHERE id = ?", String.class, id))
                .isEqualTo("PUBLIC");
    }

    @Test
    void report_status_기본값은_PENDING이다() {
        long poll = poll(member());
        long id = insert("INSERT INTO report (poll_id, reporter_anon_id, reason, created_at, updated_at) "
                + "VALUES (" + poll + ", '" + uuid() + "', 'SPAM', NOW(), NOW())");

        assertThat(jdbc.queryForObject("SELECT status FROM report WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void poll_close_at은_NULL을_허용한다() {
        assertThatCode(() -> poll(member())).doesNotThrowAnyException();
    }

    // ---------- member_auth ----------

    @Test
    void 같은_제공자_사용자_ID로_member_auth를_두_번_만들_수_없다() {
        long m1 = member();
        long m2 = member();
        memberAuth(m1, "KAKAO", "kakao-1");

        assertThatThrownBy(() -> memberAuth(m2, "KAKAO", "kakao-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 한_회원이_같은_제공자를_두_번_연동할_수_없다() {
        long m = member();
        memberAuth(m, "KAKAO", "kakao-1");

        assertThatThrownBy(() -> memberAuth(m, "KAKAO", "kakao-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_제공자_사용자_ID이면_다른_회원이_member_auth를_만들_수_있다() {
        memberAuth(member(), "KAKAO", "kakao-1");

        assertThatCode(() -> memberAuth(member(), "KAKAO", "kakao-2")).doesNotThrowAnyException();
    }

    @Test
    void 존재하지_않는_회원의_member_auth는_거부된다() {
        assertThatThrownBy(() -> memberAuth(999_999L, "KAKAO", "kakao-x"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- refresh_token ----------

    @Test
    void 같은_회원이_refresh_token을_여러_건_가질_수_있다() {
        long m = member();
        refreshToken(m, hash('a'));

        assertThatCode(() -> refreshToken(m, hash('b'))).doesNotThrowAnyException();
    }

    @Test
    void token_hash가_같은_refresh_token은_거부된다() {
        refreshToken(member(), hash('a'));

        assertThatThrownBy(() -> refreshToken(member(), hash('a')))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- poll ----------

    @Test
    void public_id가_같은_poll은_거부된다() {
        long author = member();
        poll(author, "abcdefghijkl");

        assertThatThrownBy(() -> poll(author, "abcdefghijkl"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 존재하지_않는_작성자의_poll은_거부된다() {
        assertThatThrownBy(() -> poll(999_999L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 투표가_있는_회원은_삭제할_수_없다_RESTRICT() {
        long author = member();
        poll(author);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM member WHERE id = ?", author))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- poll_option ----------

    @Test
    void position_1과_5는_허용된다() {
        long poll = poll(member());

        assertThatCode(() -> {
            option(poll, 1, "a");
            option(poll, 5, "e");
        }).doesNotThrowAnyException();
    }

    @Test
    void position_0은_CHECK로_거부된다() {
        long poll = poll(member());

        assertThatThrownBy(() -> option(poll, 0, "a"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void position_6은_CHECK로_거부된다() {
        long poll = poll(member());

        assertThatThrownBy(() -> option(poll, 6, "a"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_투표에_같은_position의_선택지는_거부된다() {
        long poll = poll(member());
        option(poll, 1, "a");

        assertThatThrownBy(() -> option(poll, 1, "b"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_투표에_같은_content의_선택지는_거부된다() {
        long poll = poll(member());
        option(poll, 1, "a");

        assertThatThrownBy(() -> option(poll, 2, "a"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_투표에는_같은_position과_content를_쓸_수_있다() {
        long author = member();
        option(poll(author), 1, "a");

        assertThatCode(() -> option(poll(author), 1, "a")).doesNotThrowAnyException();
    }

    @Test
    void 존재하지_않는_투표의_선택지는_거부된다() {
        assertThatThrownBy(() -> option(999_999L, 1, "a"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- vote ----------

    @Test
    void 회원_투표를_저장할_수_있다() {
        long poll = poll(member());
        long opt = option(poll, 1, "a");

        assertThatCode(() -> vote(poll, opt, member(), null)).doesNotThrowAnyException();
    }

    @Test
    void 비회원_투표를_저장할_수_있다() {
        long poll = poll(member());
        long opt = option(poll, 1, "a");

        assertThatCode(() -> vote(poll, opt, null, uuid())).doesNotThrowAnyException();
    }

    @Test
    void 같은_투표에_회원과_비회원이_함께_참여할_수_있다_NULL은_유니크에_영향이_없다() {
        long poll = poll(member());
        long opt = option(poll, 1, "a");
        vote(poll, opt, member(), null);
        vote(poll, opt, member(), null);
        vote(poll, opt, null, uuid());

        assertThatCode(() -> vote(poll, opt, null, uuid())).doesNotThrowAnyException();
    }

    @Test
    void 같은_회원이_같은_투표에_두_번_참여할_수_없다() {
        long poll = poll(member());
        long opt1 = option(poll, 1, "a");
        long opt2 = option(poll, 2, "b");
        long voter = member();
        vote(poll, opt1, voter, null);

        assertThatThrownBy(() -> vote(poll, opt2, voter, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_익명_식별자가_같은_투표에_두_번_참여할_수_없다() {
        long poll = poll(member());
        long opt1 = option(poll, 1, "a");
        long opt2 = option(poll, 2, "b");
        String anon = uuid();
        vote(poll, opt1, null, anon);

        assertThatThrownBy(() -> vote(poll, opt2, null, anon))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_회원이_다른_투표에는_참여할_수_있다() {
        long author = member();
        long voter = member();
        long poll1 = poll(author);
        long poll2 = poll(author);
        vote(poll1, option(poll1, 1, "a"), voter, null);

        assertThatCode(() -> vote(poll2, option(poll2, 1, "a"), voter, null)).doesNotThrowAnyException();
    }

    @Test
    void member_id와_anon_id가_모두_NULL이면_CHECK로_거부된다() {
        long poll = poll(member());
        long opt = option(poll, 1, "a");

        assertThatThrownBy(() -> vote(poll, opt, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void member_id와_anon_id가_모두_있으면_CHECK로_거부된다() {
        long poll = poll(member());
        long opt = option(poll, 1, "a");

        assertThatThrownBy(() -> vote(poll, opt, member(), uuid()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_투표의_선택지를_고른_투표_기록은_복합_FK로_거부된다() {
        long author = member();
        long pollA = poll(author);
        long pollB = poll(author);
        long optionOfB = option(pollB, 1, "a");

        assertThatThrownBy(() -> vote(pollA, optionOfB, member(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 존재하지_않는_회원의_투표_기록은_거부된다() {
        long poll = poll(member());
        long opt = option(poll, 1, "a");

        assertThatThrownBy(() -> vote(poll, opt, 999_999L, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 투표_기록이_있는_선택지는_삭제할_수_없다_RESTRICT() {
        long poll = poll(member());
        long opt = option(poll, 1, "a");
        vote(poll, opt, member(), null);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM poll_option WHERE id = ?", opt))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- report ----------

    @Test
    void 같은_회원이_같은_투표를_두_번_신고할_수_없다() {
        long poll = poll(member());
        long reporter = member();
        report(poll, reporter, null);

        assertThatThrownBy(() -> report(poll, reporter, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_익명_식별자가_같은_투표를_두_번_신고할_수_없다() {
        long poll = poll(member());
        String anon = uuid();
        report(poll, null, anon);

        assertThatThrownBy(() -> report(poll, null, anon))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 회원과_비회원은_같은_투표를_각각_신고할_수_있다() {
        long poll = poll(member());
        report(poll, member(), null);

        assertThatCode(() -> report(poll, null, uuid())).doesNotThrowAnyException();
    }

    @Test
    void 신고자_신원이_모두_NULL이면_CHECK로_거부된다() {
        long poll = poll(member());

        assertThatThrownBy(() -> report(poll, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 신고자_신원이_모두_있으면_CHECK로_거부된다() {
        long poll = poll(member());

        assertThatThrownBy(() -> report(poll, member(), uuid()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- admin_action ----------

    @Test
    void 존재하지_않는_관리자의_admin_action은_거부된다() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO admin_action (admin_id, action_type, created_at, updated_at) VALUES (999999, 'HIDE_POLL', NOW(), NOW())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- helpers ----------

    private boolean hasIndex(String table, String... columns) {
        List<String> names = jdbc.queryForList(
                "SELECT DISTINCT INDEX_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND LOWER(TABLE_NAME) = ?",
                String.class, table);
        for (String name : names) {
            List<String> cols = jdbc.queryForList(
                    "SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                            + "WHERE TABLE_SCHEMA = SCHEMA() AND LOWER(TABLE_NAME) = ? AND INDEX_NAME = ? "
                            + "ORDER BY ORDINAL_POSITION",
                    String.class, table, name);
            if (cols.equals(List.of(columns))) {
                return true;
            }
        }
        return false;
    }

    private long insert(String sql) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keys);
        return ((Number) keys.getKeys().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("id"))
                .findFirst().orElseThrow().getValue()).longValue();
    }

    private long member() {
        return insert("INSERT INTO member (status, role, created_at, updated_at) "
                + "VALUES ('ACTIVE', 'MEMBER', NOW(), NOW())");
    }

    private void memberAuth(long memberId, String provider, String providerUserId) {
        jdbc.update("INSERT INTO member_auth (member_id, provider, provider_user_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, NOW(), NOW())", memberId, provider, providerUserId);
    }

    private void refreshToken(long memberId, String tokenHash) {
        jdbc.update("INSERT INTO refresh_token (member_id, token_hash, expires_at, created_at, updated_at) "
                + "VALUES (?, ?, NOW(), NOW(), NOW())", memberId, tokenHash);
    }

    private long poll(long authorId) {
        return poll(authorId, publicId());
    }

    private long poll(long authorId, String publicId) {
        return insert("INSERT INTO poll (author_id, question, category, status, created_at, updated_at, public_id) "
                + "VALUES (" + authorId + ", 'q', 'SOME', 'PUBLIC', NOW(), NOW(), '" + publicId + "')");
    }

    private long option(long pollId, int position, String content) {
        return insert("INSERT INTO poll_option (poll_id, position, content, created_at, updated_at) VALUES ("
                + pollId + ", " + position + ", '" + content + "', NOW(), NOW())");
    }

    private long vote(long pollId, long optionId, Long memberId, String anonId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO vote (poll_id, option_id, member_id, anon_id, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, NOW(), NOW())", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, pollId);
            ps.setLong(2, optionId);
            ps.setObject(3, memberId);
            ps.setObject(4, anonId);
            return ps;
        }, keys);
        return 0L;
    }

    private void report(long pollId, Long memberId, String anonId) {
        jdbc.update("INSERT INTO report (poll_id, reporter_member_id, reporter_anon_id, reason, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'SPAM', 'PENDING', NOW(), NOW())", pollId, memberId, anonId);
    }

    private String publicId() {
        return String.format("%012d", ++seq);
    }

    private String uuid() {
        return java.util.UUID.randomUUID().toString();
    }

    private String hash(char c) {
        return String.valueOf(c).repeat(64);
    }
}
