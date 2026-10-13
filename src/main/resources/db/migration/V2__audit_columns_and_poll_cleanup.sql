-- V2: 엔티티 공통 감사 컬럼(created_at·updated_at, BaseEntity)과 poll 컬럼 정리.
-- 기존 행은 마이그레이션 시각으로 채운 뒤 DEFAULT는 제거한다(시각은 앱이 KST로 채운다).

ALTER TABLE member_auth   ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE refresh_token ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE poll          ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE poll_option   ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE poll_option   ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE vote          ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE report        ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE admin_action  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE member_auth   ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE refresh_token ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE poll          ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE poll_option   ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE poll_option   ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE vote          ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE report        ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE admin_action  ALTER COLUMN updated_at DROP DEFAULT;

-- 질문은 500자까지 허용한다.
ALTER TABLE poll MODIFY COLUMN question VARCHAR(500) NOT NULL;

-- 상태 변경 일시·주체는 두지 않는다(updated_at과 AdminAction 이력으로 충분하다).
ALTER TABLE poll DROP FOREIGN KEY fk_poll_status_changed_by;
ALTER TABLE poll DROP COLUMN status_changed_at;
ALTER TABLE poll DROP COLUMN status_changed_by;
