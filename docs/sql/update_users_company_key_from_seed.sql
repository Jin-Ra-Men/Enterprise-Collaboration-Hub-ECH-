-- users.company_key 를 `seed_test_users.sql` 과 동일하게 갱신 (PostgreSQL)
-- `migrate_users_company_key.sql` 실행 후 사용합니다.

UPDATE users AS u
SET
    company_key = v.company_key,
    updated_at = NOW()
FROM (
    VALUES
        ('ECH-ADM-001', 'GENERAL'::VARCHAR(40)),
        ('ECH-TST-001', 'GENERAL'),
        ('ECH-TST-002', 'GENERAL'),
        ('ECH-TST-003', 'GENERAL'),
        ('ECH-DEV-001', 'GENERAL'),
        ('ECH-DEV-002', 'GENERAL'),
        ('ECH-DEV-003', 'GENERAL'),
        ('ECH-DEV-004', 'GENERAL'),
        ('ECH-DEV-005', 'GENERAL'),
        ('ECH-HR-001', 'GENERAL'),
        ('ECH-SAL-001', 'GENERAL'),
        ('ECH-PLN-001', 'GENERAL'),
        ('ECH-SEC-001', 'GENERAL'),
        ('ECH-EXT-001', 'EXTERNAL'),
        ('ECH-INA-001', 'GENERAL')
) AS v(employee_no, company_key)
WHERE u.employee_no = v.employee_no;
