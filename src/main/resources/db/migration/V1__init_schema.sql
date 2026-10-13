-- AuDDum V1 스키마 (docs/ERD.md §4·§5)
-- 시각은 KST 기준 DATETIME(6), PK는 BIGINT AUTO_INCREMENT, 상태값은 VARCHAR 코드 문자열이다.
-- 삭제는 논리 삭제이므로 외래키는 기본 동작(RESTRICT)을 쓴다.
-- 인덱스는 제약용(UNIQUE, FK)만 둔다. 조회 최적화 인덱스는 EXPLAIN으로 확인한 뒤 새 마이그레이션으로 추가한다(ERD §5).

CREATE TABLE member (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    status        VARCHAR(20) NOT NULL,
    role          VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    last_login_at DATETIME(6) NULL,
    suspended_at  DATETIME(6) NULL,
    withdrawn_at  DATETIME(6) NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL
);

CREATE TABLE member_auth (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id        BIGINT      NOT NULL,
    provider         VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(64) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    CONSTRAINT uq_member_auth_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_member_auth_member_provider UNIQUE (member_id, provider),
    CONSTRAINT fk_member_auth_member FOREIGN KEY (member_id) REFERENCES member (id)
);

CREATE TABLE refresh_token (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_member FOREIGN KEY (member_id) REFERENCES member (id)
);
CREATE INDEX ix_refresh_token_member ON refresh_token (member_id);

CREATE TABLE poll (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id         BIGINT       NOT NULL,
    question          VARCHAR(100) NOT NULL,
    category          VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    close_at          DATETIME(6)  NULL,
    status_changed_at DATETIME(6)  NULL,
    status_changed_by BIGINT       NULL,
    created_at        DATETIME(6)  NOT NULL,
    public_id         VARCHAR(36)  NOT NULL,
    CONSTRAINT uq_poll_public_id UNIQUE (public_id),
    CONSTRAINT fk_poll_author FOREIGN KEY (author_id) REFERENCES member (id),
    CONSTRAINT fk_poll_status_changed_by FOREIGN KEY (status_changed_by) REFERENCES member (id)
);

CREATE TABLE poll_option (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id  BIGINT      NOT NULL,
    position TINYINT     NOT NULL,
    content  VARCHAR(50) NOT NULL,
    CONSTRAINT uq_poll_option_position UNIQUE (poll_id, position),
    CONSTRAINT uq_poll_option_content UNIQUE (poll_id, content),
    CONSTRAINT uq_poll_option_poll_id UNIQUE (poll_id, id),
    CONSTRAINT ck_poll_option_position CHECK (position BETWEEN 1 AND 5),
    CONSTRAINT fk_poll_option_poll FOREIGN KEY (poll_id) REFERENCES poll (id)
);

CREATE TABLE vote (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id    BIGINT      NOT NULL,
    option_id  BIGINT      NOT NULL,
    member_id  BIGINT      NULL,
    anon_id    CHAR(36)    NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_vote_member UNIQUE (poll_id, member_id),
    CONSTRAINT uq_vote_anon UNIQUE (poll_id, anon_id),
    CONSTRAINT fk_vote_option FOREIGN KEY (poll_id, option_id) REFERENCES poll_option (poll_id, id),
    CONSTRAINT fk_vote_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT ck_vote_voter CHECK ((member_id IS NULL) <> (anon_id IS NULL))
);
CREATE INDEX ix_vote_result ON vote (poll_id, option_id);

CREATE TABLE report (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id            BIGINT       NOT NULL,
    reporter_member_id BIGINT       NULL,
    reporter_anon_id   CHAR(36)     NULL,
    reason             VARCHAR(30)  NOT NULL,
    detail             VARCHAR(200) NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at         DATETIME(6)  NOT NULL,
    handled_at         DATETIME(6)  NULL,
    handled_by         BIGINT       NULL,
    CONSTRAINT uq_report_member UNIQUE (poll_id, reporter_member_id),
    CONSTRAINT uq_report_anon UNIQUE (poll_id, reporter_anon_id),
    CONSTRAINT ck_report_reporter CHECK ((reporter_member_id IS NULL) <> (reporter_anon_id IS NULL)),
    CONSTRAINT fk_report_poll FOREIGN KEY (poll_id) REFERENCES poll (id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_member_id) REFERENCES member (id),
    CONSTRAINT fk_report_handled_by FOREIGN KEY (handled_by) REFERENCES member (id)
);

CREATE TABLE admin_action (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id         BIGINT       NOT NULL,
    action_type      VARCHAR(30)  NOT NULL,
    target_poll_id   BIGINT       NULL,
    target_member_id BIGINT       NULL,
    report_id        BIGINT       NULL,
    reason           VARCHAR(200) NULL,
    created_at       DATETIME(6)  NOT NULL,
    CONSTRAINT fk_admin_action_admin FOREIGN KEY (admin_id) REFERENCES member (id),
    CONSTRAINT fk_admin_action_poll FOREIGN KEY (target_poll_id) REFERENCES poll (id),
    CONSTRAINT fk_admin_action_member FOREIGN KEY (target_member_id) REFERENCES member (id),
    CONSTRAINT fk_admin_action_report FOREIGN KEY (report_id) REFERENCES report (id)
);
CREATE INDEX ix_admin_action_poll ON admin_action (target_poll_id);
CREATE INDEX ix_admin_action_member ON admin_action (target_member_id);
