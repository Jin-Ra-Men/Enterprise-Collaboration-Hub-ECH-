-- 대량 타임라인·스크롤 성능 테스트용 ROOT 메시지 10만 건 (PostgreSQL)
--
-- 1) 아래 두 값을 실제 채널·사용자에 맞게 수정한다.
-- 2) psql 또는 클라이언트에서 스크립트 전체 실행.
--
-- 주의: 실행 시간·WAL·디스크 사용이 큼. 테스트/스테이징 DB 권장.

INSERT INTO messages (
    channel_id,
    sender_id,
    parent_message_id,
    body,
    message_type,
    is_edited,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    1::bigint,                    -- TODO: 대상 channel_id
    'ECH-ADM-001'::varchar(50),   -- TODO: 존재하는 users.employee_no
    NULL,
    'load-test ' || g::text,
    'TEXT',
    false,
    false,
    NOW() - (g || ' seconds')::interval,
    NOW() - (g || ' seconds')::interval
FROM generate_series(1, 100000) AS g;
