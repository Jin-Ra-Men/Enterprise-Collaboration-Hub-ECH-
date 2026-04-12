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
                        "CSTalk-ADM-001", "admin@cstalk-test.local", "시스템 관리자",
                        "CSTalk 주식회사", "운영본부", "IT운영팀",
                        "부장", null, null,
                        "ADMIN", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-TST-001", "kim.test@cstalk-test.local", "김테스트",
                        "CSTalk 주식회사", "품질본부", "테스트팀",
                        "대리", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-TST-002", "han.intern@cstalk-test.local", "한인턴",
                        "CSTalk 주식회사", "품질본부", "테스트팀",
                        "인턴", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-TST-003", "song.qalead@cstalk-test.local", "송QA리드",
                        "CSTalk 주식회사", "품질본부", "테스트팀",
                        "과장", null, "QA 리드",
                        "MANAGER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-DEV-001", "lee.dev@cstalk-test.local", "이개발",
                        "CSTalk 주식회사", "기술본부", "개발1팀",
                        "대리", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-DEV-002", "park.backend@cstalk-test.local", "박백엔드",
                        "CSTalk 주식회사", "기술본부", "개발1팀",
                        "사원", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-DEV-003", "cho.lead@cstalk-test.local", "조팀장",
                        "CSTalk 주식회사", "기술본부", "개발1팀",
                        "차장", null, "개발1팀 팀장",
                        "MANAGER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-DEV-004", "choi.front@cstalk-test.local", "최프론트",
                        "CSTalk 주식회사", "기술본부", "개발2팀",
                        "대리", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-DEV-005", "jung.fullstack@cstalk-test.local", "정풀스택",
                        "CSTalk 주식회사", "기술본부", "개발2팀",
                        "사원", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-HR-001", "jung.hr@cstalk-test.local", "정인사",
                        "CSTalk 주식회사", "경영지원본부", "인사총무팀",
                        "과장", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-SAL-001", "kang.sales@cstalk-test.local", "강영업",
                        "CSTalk 주식회사", "영업본부", "영업1팀",
                        "차장", "부사장", "영업1팀 팀장",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-PLN-001", "yoon.pm@cstalk-test.local", "윤기획",
                        "CSTalk 주식회사", "기획본부", "기획전략팀",
                        "부장", "사장", "기획전략팀 팀장",
                        "MANAGER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-SEC-001", "lim.security@cstalk-test.local", "임보안",
                        "CSTalk 주식회사", "감사본부", "보안감사팀",
                        "대리", null, "팀원",
                        "MEMBER", "ACTIVE", "GENERAL"),
                new ExternalOrgUser(
                        "CSTalk-EXT-001", "consultant@cstalk-test.local", "외부컨설턴트",
                        null, null, null,
                        null, null, null,
                        "MEMBER", "ACTIVE", "EXTERNAL"),
                new ExternalOrgUser(
                        "CSTalk-INA-001", "retired.user@cstalk-test.local", "퇴사처리유저",
                        "CSTalk 주식회사", "경영지원본부", "인사총무팀",
                        "대리", null, "팀원",
                        "MEMBER", "INACTIVE", "GENERAL")
        );
    }
}
