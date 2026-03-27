-- 채널 타입 체크 제약에 DM 허용 추가
-- 오래된 로컬 DB에서 channels_channel_type_check가 PUBLIC/PRIVATE만 허용해
-- DM 생성이 실패하는 문제를 보정한다.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'channels'
          AND constraint_name = 'channels_channel_type_check'
    ) THEN
        ALTER TABLE channels DROP CONSTRAINT channels_channel_type_check;
    END IF;

    ALTER TABLE channels
        ADD CONSTRAINT channels_channel_type_check
            CHECK (channel_type IN ('PUBLIC', 'PRIVATE', 'DM'));
END $$;
