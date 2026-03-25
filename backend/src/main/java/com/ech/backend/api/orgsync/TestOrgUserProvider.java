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
                new ExternalOrgUser("ECH-ADM-001", "admin.ech@ech-test.local", "시스템 관리자", "운영본부", "부장", null, "ADMIN", "ACTIVE"),
                new ExternalOrgUser("ECH-TST-001", "kim.test@ech-test.local", "김테스트", "테스트부서", "대리", null, "MEMBER", "ACTIVE"),
                new ExternalOrgUser("ECH-TST-002", "han.intern@ech-test.local", "한인턴", "테스트부서", "인턴", null, "MEMBER", "ACTIVE"),
                new ExternalOrgUser("ECH-TST-003", "song.qalead@ech-test.local", "송QA리드", "테스트부서", "과장", "QA 리드", "MANAGER", "ACTIVE"),
                new ExternalOrgUser("ECH-DEV-001", "lee.dev@ech-test.local", "이개발", "개발1팀", "대리", null, "MEMBER", "ACTIVE"),
                new ExternalOrgUser("ECH-DEV-002", "park.backend@ech-test.local", "박백엔드", "개발1팀", "사원", null, "MEMBER", "ACTIVE"),
                new ExternalOrgUser("ECH-DEV-003", "cho.lead@ech-test.local", "조팀장", "개발1팀", "차장", "개발1팀 팀장", "MANAGER", "ACTIVE"),
                new ExternalOrgUser("ECH-DEV-004", "choi.front@ech-test.local", "최프론트", "개발2팀", "대리", null, "MEMBER", "ACTIVE"),
                new ExternalOrgUser("ECH-DEV-005", "jung.fullstack@ech-test.local", "정풀스택", "개발2팀", "사원", null, "MEMBER", "ACTIVE"),
                new ExternalOrgUser("ECH-HR-001", "jung.hr@ech-test.local", "정인사", "인사총무팀", "과장", null, "MEMBER", "ACTIVE"),
                new ExternalOrgUser("ECH-SAL-001", "kang.sales@ech-test.local", "강영업", "영업1팀", "차장", "영업1팀 팀장", "MEMBER", "ACTIVE"),
                new ExternalOrgUser("ECH-PLN-001", "yoon.pm@ech-test.local", "윤기획", "기획전략팀", "부장", null, "MANAGER", "ACTIVE"),
                new ExternalOrgUser("ECH-SEC-001", "lim.security@ech-test.local", "임보안", "보안감사팀", "대리", null, "MEMBER", "ACTIVE"),
                new ExternalOrgUser("ECH-EXT-001", "consultant@ech-test.local", "외부컨설턴트", null, null, null, "MEMBER", "ACTIVE"),
                new ExternalOrgUser("ECH-INA-001", "retired.user@ech-test.local", "퇴사처리유저", "인사총무팀", "대리", null, "MEMBER", "INACTIVE")
        );
    }
}
