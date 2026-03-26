package com.ech.backend.api.orgsync;

import com.ech.backend.api.orgsync.dto.ExternalOrgUser;
import com.ech.backend.api.orgsync.dto.OrgSyncSource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TestOrgUserProvider implements OrgUserProvider {

    @Override
    public OrgSyncSource source() {
        return OrgSyncSource.TEST;
    }

    @Override
    public List<ExternalOrgUser> fetchUsers() {
        return List.of(
                new ExternalOrgUser(
                        "ECH-ADM-001", "admin.ech@ech-test.local", "시스템 관리자",
                        "ECH 주식회사", "운영본부", "IT운영팀",
                        "부장", null, null,
                        "ADMIN", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-TST-001", "kim.test@ech-test.local", "김테스트",
                        "ECH 주식회사", "품질본부", "테스트팀",
                        "대리", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-TST-002", "han.intern@ech-test.local", "한인턴",
                        "ECH 주식회사", "품질본부", "테스트팀",
                        "인턴", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-TST-003", "song.qalead@ech-test.local", "송QA리드",
                        "ECH 주식회사", "품질본부", "테스트팀",
                        "과장", null, "QA 리드",
                        "MANAGER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-DEV-001", "lee.dev@ech-test.local", "이개발",
                        "ECH 주식회사", "기술본부", "개발1팀",
                        "대리", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-DEV-002", "park.backend@ech-test.local", "박백엔드",
                        "ECH 주식회사", "기술본부", "개발1팀",
                        "사원", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-DEV-003", "cho.lead@ech-test.local", "조팀장",
                        "ECH 주식회사", "기술본부", "개발1팀",
                        "차장", null, "개발1팀 팀장",
                        "MANAGER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-DEV-004", "choi.front@ech-test.local", "최프론트",
                        "ECH 주식회사", "기술본부", "개발2팀",
                        "대리", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-DEV-005", "jung.fullstack@ech-test.local", "정풀스택",
                        "ECH 주식회사", "기술본부", "개발2팀",
                        "사원", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-HR-001", "jung.hr@ech-test.local", "정인사",
                        "ECH 주식회사", "경영지원본부", "인사총무팀",
                        "과장", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-SAL-001", "kang.sales@ech-test.local", "강영업",
                        "ECH 주식회사", "영업본부", "영업1팀",
                        "차장", "부사장", "영업1팀 팀장",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-PLN-001", "yoon.pm@ech-test.local", "윤기획",
                        "ECH 주식회사", "기획본부", "기획전략팀",
                        "부장", "사장", "기획전략팀 팀장",
                        "MANAGER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-SEC-001", "lim.security@ech-test.local", "임보안",
                        "ECH 주식회사", "감사본부", "보안감사팀",
                        "대리", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "ECH-EXT-001", "consultant@ech-test.local", "외부컨설턴트",
                        null, null, null,
                        null, null, null,
                        "MEMBER", "ACTIVE", "EXTERNAL"),
                new ExternalOrgUser(
                        "ECH-INA-001", "retired.user@ech-test.local", "퇴사처리유저",
                        "ECH 주식회사", "경영지원본부", "인사총무팀",
                        "대리", null, "팀원",
                        "MEMBER", "INACTIVE", "GENERAL")
        );
    }
}
