-- DUTY_TITLE 조직 타입을 JOB_TITLE 로 통일 (기존 group_code 는 유지)
--
--   psql ... -f docs/sql/migrate_org_duty_title_to_job_title.sql

UPDATE org_groups SET group_type = 'JOB_TITLE' WHERE group_type = 'DUTY_TITLE';
UPDATE org_group_members SET member_group_type = 'JOB_TITLE' WHERE member_group_type = 'DUTY_TITLE';
