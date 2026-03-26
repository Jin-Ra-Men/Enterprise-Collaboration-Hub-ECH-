-- ECH 로컬·통합 테스트용 사용자 시드 (그룹웨어/조직도 미연동 시)
-- 사번(employee_no) 기준으로 재실행 시 내용이 갱신됩니다(UPSERT).
-- 회사·본부·팀·직급 정보는 users 가 아니라 org_groups / org_group_members 및 조직 동기화로 채웁니다.
--
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/seed_test_users.sql
--
-- 운영 DB에는 실행하지 마세요.

INSERT INTO users (employee_no, email, name, role, status, created_at, updated_at)
VALUES
    ('ECH-ADM-001', 'admin.ech@ech-test.local', '시스템 관리자', 'ADMIN', 'ACTIVE', NOW(), NOW()),
    ('ECH-TST-001', 'kim.test@ech-test.local', '김테스트', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-TST-002', 'han.intern@ech-test.local', '한인턴', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-TST-003', 'song.qalead@ech-test.local', '송QA리드', 'MANAGER', 'ACTIVE', NOW(), NOW()),
    ('ECH-DEV-001', 'lee.dev@ech-test.local', '이개발', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-DEV-002', 'park.backend@ech-test.local', '박백엔드', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-DEV-003', 'cho.lead@ech-test.local', '조팀장', 'MANAGER', 'ACTIVE', NOW(), NOW()),
    ('ECH-DEV-004', 'choi.front@ech-test.local', '최프론트', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-DEV-005', 'jung.fullstack@ech-test.local', '정풀스택', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-HR-001', 'jung.hr@ech-test.local', '정인사', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-SAL-001', 'kang.sales@ech-test.local', '강영업', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-PLN-001', 'yoon.pm@ech-test.local', '윤기획', 'MANAGER', 'ACTIVE', NOW(), NOW()),
    ('ECH-SEC-001', 'lim.security@ech-test.local', '임보안', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-EXT-001', 'consultant@ech-test.local', '외부컨설턴트', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-INA-001', 'retired.user@ech-test.local', '퇴사처리유저', 'MEMBER', 'INACTIVE', NOW(), NOW())
ON CONFLICT (employee_no) DO UPDATE SET
    email      = EXCLUDED.email,
    name       = EXCLUDED.name,
    role       = EXCLUDED.role,
    status     = EXCLUDED.status,
    updated_at = NOW();
