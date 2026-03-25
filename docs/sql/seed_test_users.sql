-- ECH 로컬·통합 테스트용 사용자 시드 (그룹웨어/조직도 미연동 시)
-- 사번(employee_no) 기준으로 재실행 시 내용이 갱신됩니다(UPSERT).
--
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/seed_test_users.sql
--
-- 운영 DB에는 실행하지 마세요.

INSERT INTO users (employee_no, email, name, department, job_rank, duty_title, role, status, created_at, updated_at)
VALUES
    -- 관리자 1명 (API·화면에서 관리자 시나리오 테스트용)
    ('ECH-ADM-001', 'admin.ech@ech-test.local', '시스템 관리자', '운영본부', '부장', NULL, 'ADMIN', 'ACTIVE', NOW(), NOW()),

    -- 테스트부서 (QA·검증 담당 역할 다양화)
    ('ECH-TST-001', 'kim.test@ech-test.local', '김테스트', '테스트부서', '대리', NULL, 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-TST-002', 'han.intern@ech-test.local', '한인턴', '테스트부서', '인턴', NULL, 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-TST-003', 'song.qalead@ech-test.local', '송QA리드', '테스트부서', '과장', 'QA 리드', 'MANAGER', 'ACTIVE', NOW(), NOW()),

    -- 개발 조직 (팀 분리)
    ('ECH-DEV-001', 'lee.dev@ech-test.local', '이개발', '개발1팀', '대리', NULL, 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-DEV-002', 'park.backend@ech-test.local', '박백엔드', '개발1팀', '사원', NULL, 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-DEV-003', 'cho.lead@ech-test.local', '조팀장', '개발1팀', '차장', '개발1팀 팀장', 'MANAGER', 'ACTIVE', NOW(), NOW()),
    ('ECH-DEV-004', 'choi.front@ech-test.local', '최프론트', '개발2팀', '대리', NULL, 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-DEV-005', 'jung.fullstack@ech-test.local', '정풀스택', '개발2팀', '사원', NULL, 'MEMBER', 'ACTIVE', NOW(), NOW()),

    -- 기타 부서 (검색·필터 테스트용)
    ('ECH-HR-001', 'jung.hr@ech-test.local', '정인사', '인사총무팀', '과장', NULL, 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-SAL-001', 'kang.sales@ech-test.local', '강영업', '영업1팀', '차장', '영업1팀 팀장', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('ECH-PLN-001', 'yoon.pm@ech-test.local', '윤기획', '기획전략팀', '부장', NULL, 'MANAGER', 'ACTIVE', NOW(), NOW()),
    ('ECH-SEC-001', 'lim.security@ech-test.local', '임보안', '보안감사팀', '대리', NULL, 'MEMBER', 'ACTIVE', NOW(), NOW()),

    -- 부서 미지정 (조직도 속성 null 케이스)
    ('ECH-EXT-001', 'consultant@ech-test.local', '외부컨설턴트', NULL, NULL, NULL, 'MEMBER', 'ACTIVE', NOW(), NOW()),

    -- 비활성 계정 (상태 필터·정책 테스트용)
    ('ECH-INA-001', 'retired.user@ech-test.local', '퇴사처리유저', '인사총무팀', '대리', NULL, 'MEMBER', 'INACTIVE', NOW(), NOW())
ON CONFLICT (employee_no) DO UPDATE SET
    email       = EXCLUDED.email,
    name        = EXCLUDED.name,
    department  = EXCLUDED.department,
    job_rank    = EXCLUDED.job_rank,
    duty_title  = EXCLUDED.duty_title,
    role        = EXCLUDED.role,
    status      = EXCLUDED.status,
    updated_at  = NOW();
