-- V3: 신고 처리의 동시 요청(더블클릭·재전송)을 낙관적 락으로 막는 version 컬럼(Report @Version).
ALTER TABLE report ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
