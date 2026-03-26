package com.ech.backend.api.orgsync;

import com.ech.backend.api.orgsync.dto.ExternalOrgUser;
import com.ech.backend.api.orgsync.dto.OrgSyncPreviewResponse;
import com.ech.backend.api.orgsync.dto.OrgSyncResultResponse;
import com.ech.backend.api.orgsync.dto.OrgSyncSource;
import com.ech.backend.domain.org.OrgGroup;
import com.ech.backend.domain.org.OrgGroupCodes;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.org.OrgGroupRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrgSyncService {

    private final UserRepository userRepository;
    private final Map<OrgSyncSource, OrgUserProvider> providerBySource;
    private final OrgGroupRepository orgGroupRepository;
    private final OrgGroupMemberRepository orgGroupMemberRepository;

    public OrgSyncService(
            UserRepository userRepository,
            OrgGroupRepository orgGroupRepository,
            OrgGroupMemberRepository orgGroupMemberRepository,
            List<OrgUserProvider> providers
    ) {
        this.userRepository = userRepository;
        this.orgGroupRepository = orgGroupRepository;
        this.orgGroupMemberRepository = orgGroupMemberRepository;
        this.providerBySource = new EnumMap<>(OrgSyncSource.class);
        for (OrgUserProvider provider : providers) {
            providerBySource.put(provider.source(), provider);
        }
    }

    public OrgSyncPreviewResponse preview(OrgSyncSource source) {
        List<ExternalOrgUser> users = getProvider(source).fetchUsers();
        return new OrgSyncPreviewResponse(source, users.size(), users);
    }

    @Transactional
    public OrgSyncResultResponse syncUsers(OrgSyncSource source) {
        List<ExternalOrgUser> users = getProvider(source).fetchUsers();
        Map<String, OrgGroup> groupCache = new HashMap<>();

        for (ExternalOrgUser user : users) {
            userRepository.upsertByEmployeeNo(
                    user.employeeNo(),
                    user.email(),
                    user.name(),
                    safeRole(user.role()),
                    safeStatus(user.status())
            );

            User saved = userRepository.findByEmployeeNo(user.employeeNo())
                    .orElseThrow(() -> new IllegalStateException("upsert 이후 사용자 조회 실패: " + user.employeeNo()));

            upsertOrgAndMembership(
                    saved.getEmployeeNo(),
                    user,
                    safeCompanyCode(user.companyCode()),
                    groupCache
            );
        }
        return new OrgSyncResultResponse(source, users.size());
    }

    private void upsertOrgAndMembership(
            String employeeNo,
            ExternalOrgUser external,
            String companyCodeNormalized,
            Map<String, OrgGroup> groupCache
    ) {
        String companyDisplayName = resolveCompanyDisplayName(external.companyName(), companyCodeNormalized);
        String divisionDisplayName = resolveOrDefault(external.divisionName(), "미지정 본부");
        String teamDisplayName = resolveOrDefault(external.teamName(), "미지정 팀");

        String companyFp = OrgGroupCodes.fingerprintCompany(companyCodeNormalized, companyDisplayName);
        String companyGroupCode = OrgGroupCodes.prettyCompany(companyCodeNormalized, companyFp);
        OrgGroup companyGroup = upsertGroup(
                groupCache,
                "COMPANY",
                companyGroupCode,
                companyDisplayName,
                null,
                companyGroupCode
        );

        String divisionFp = OrgGroupCodes.fingerprintDivision(companyFp, divisionDisplayName);
        String divisionGroupCode = OrgGroupCodes.prettyDivision(companyFp, divisionFp);
        String divisionPath = companyGroupCode + ";" + divisionGroupCode;
        OrgGroup divisionGroup = upsertGroup(
                groupCache,
                "DIVISION",
                divisionGroupCode,
                divisionDisplayName,
                companyGroupCode,
                divisionPath
        );

        String teamFp = OrgGroupCodes.fingerprintTeam(divisionFp, teamDisplayName);
        String teamGroupCode = OrgGroupCodes.prettyTeam(divisionFp, teamFp);
        String teamPath = divisionPath + ";" + teamGroupCode;
        OrgGroup teamGroup = upsertGroup(
                groupCache,
                "TEAM",
                teamGroupCode,
                teamDisplayName,
                divisionGroupCode,
                teamPath
        );

        upsertMembership(employeeNo, teamGroupCode, "TEAM");

        String jobLevel = trimOrNull(external.jobLevel());
        if (jobLevel != null) {
            String jobFp = OrgGroupCodes.fingerprintJobLevel(jobLevel);
            String jobCode = OrgGroupCodes.prettyJobLevel(jobFp);
            OrgGroup jobGroup = upsertGroup(
                    groupCache,
                    "JOB_LEVEL",
                    jobCode,
                    jobLevel,
                    null,
                    null
            );
            upsertMembership(employeeNo, jobCode, "JOB_LEVEL");
        }

        String jobPosition = trimOrNull(external.jobPosition());
        if (jobPosition != null) {
            String posFp = OrgGroupCodes.fingerprintJobPosition(jobPosition);
            String posCode = OrgGroupCodes.prettyJobPosition(posFp);
            OrgGroup posGroup = upsertGroup(
                    groupCache,
                    "JOB_POSITION",
                    posCode,
                    jobPosition,
                    null,
                    null
            );
            upsertMembership(employeeNo, posCode, "JOB_POSITION");
        }

        String jobTitle = trimOrNull(external.jobTitle());
        if (jobTitle != null) {
            String titleFp = OrgGroupCodes.fingerprintJobTitle(jobTitle);
            String titleCode = OrgGroupCodes.prettyJobTitle(titleFp);
            OrgGroup titleGroup = upsertGroup(
                    groupCache,
                    "JOB_TITLE",
                    titleCode,
                    jobTitle,
                    null,
                    null
            );
            upsertMembership(employeeNo, titleCode, "JOB_TITLE");
        }
    }

    private void upsertMembership(String employeeNo, String groupCode, String memberGroupType) {
        OrgGroup group = orgGroupRepository.findByGroupTypeAndGroupCode(memberGroupType, groupCode)
                .orElseThrow(() -> new IllegalStateException("org_group not found: type=" + memberGroupType + ", code=" + groupCode));

        Optional<OrgGroupMember> existing = orgGroupMemberRepository.findByUser_EmployeeNoAndMemberGroupType(
                employeeNo,
                memberGroupType
        );
        if (existing.isPresent()) {
            OrgGroupMember m = existing.get();
            if (!m.getGroup().getGroupCode().equals(groupCode)) {
                m.setGroup(group);
                orgGroupMemberRepository.save(m);
            }
            return;
        }
        orgGroupMemberRepository.save(new OrgGroupMember(nullSafeUserByEmployeeNo(employeeNo), group, memberGroupType));
    }

    private User nullSafeUserByEmployeeNo(String employeeNo) {
        return userRepository.findByEmployeeNo(employeeNo)
                .orElseThrow(() -> new IllegalStateException("user not found: " + employeeNo));
    }

    private OrgGroup upsertGroup(
            Map<String, OrgGroup> cache,
            String groupType,
            String groupCode,
            String displayName,
            String memberOfGroupCode,
            String groupPath
    ) {
        String cacheKey = groupType + ":" + groupCode;
        OrgGroup cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Optional<OrgGroup> found = orgGroupRepository.findByGroupTypeAndGroupCode(groupType, groupCode);
        OrgGroup group = found.orElseGet(() ->
                new OrgGroup(
                        groupType,
                        groupCode,
                        displayName,
                        memberOfGroupCode,
                        groupPath
                )
        );

        group.setDisplayName(displayName);
        group.setMemberOfGroupCode(memberOfGroupCode);
        group.setGroupPath(groupPath);

        OrgGroup saved = orgGroupRepository.save(group);
        cache.put(cacheKey, saved);
        return saved;
    }

    private static String resolveCompanyDisplayName(String companyName, String companyCodeNormalized) {
        String cn = trimOrNull(companyName);
        if (cn != null) {
            return cn;
        }
        if ("EXTERNAL".equals(companyCodeNormalized)) {
            return "외부인력";
        }
        if ("COVIM365".equals(companyCodeNormalized)) {
            return "M365";
        }
        return "내부";
    }

    private static String resolveOrDefault(String value, String defaultValue) {
        String v = trimOrNull(value);
        return (v != null) ? v : defaultValue;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public void updateUserStatus(String employeeNo, String status) {
        int updated = userRepository.updateStatusByEmployeeNo(employeeNo, safeStatus(status));
        if (updated == 0) {
            throw new IllegalArgumentException("해당 사번의 사용자를 찾을 수 없습니다.");
        }
    }

    private OrgUserProvider getProvider(OrgSyncSource source) {
        OrgUserProvider provider = providerBySource.get(source);
        if (provider == null) {
            if (source == OrgSyncSource.GROUPWARE) {
                throw new IllegalArgumentException("GROUPWARE 연동 제공자는 아직 구현되지 않았습니다. 현재는 TEST 소스를 사용해주세요.");
            }
            throw new IllegalArgumentException("지원하지 않는 조직 동기화 소스입니다.");
        }
        return provider;
    }

    private static String safeCompanyCode(String companyCode) {
        if (companyCode == null || companyCode.isBlank()) {
            return "GENERAL";
        }
        return companyCode.trim().toUpperCase();
    }

    private static String safeRole(String role) {
        if (role == null || role.isBlank()) {
            return "MEMBER";
        }
        return role.trim();
    }

    private static String safeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.trim().toUpperCase();
        if (!normalized.equals("ACTIVE") && !normalized.equals("INACTIVE")) {
            throw new IllegalArgumentException("status는 ACTIVE 또는 INACTIVE여야 합니다.");
        }
        return normalized;
    }
}
