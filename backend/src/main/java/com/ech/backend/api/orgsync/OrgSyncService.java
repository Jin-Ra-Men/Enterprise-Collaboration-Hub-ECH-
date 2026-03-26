package com.ech.backend.api.orgsync;

import com.ech.backend.api.orgsync.dto.ExternalOrgUser;
import com.ech.backend.api.orgsync.dto.OrgSyncPreviewResponse;
import com.ech.backend.api.orgsync.dto.OrgSyncResultResponse;
import com.ech.backend.api.orgsync.dto.OrgSyncSource;
import com.ech.backend.domain.org.OrgGroup;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.org.OrgGroupRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HexFormat;
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
                    user.department(),
                    user.companyName(),
                    user.divisionName(),
                    user.teamName(),
                    safeCompanyCode(user.companyCode()),
                    user.jobRank(),
                    user.dutyTitle(),
                    safeRole(user.role()),
                    safeStatus(user.status())
            );

            User saved = userRepository.findByEmployeeNo(user.employeeNo())
                    .orElseThrow(() -> new IllegalStateException("upsert 이후 사용자 조회 실패: " + user.employeeNo()));

            upsertOrgAndMembership(
                    saved,
                    user,
                    safeCompanyCode(user.companyCode()),
                    groupCache
            );
        }
        return new OrgSyncResultResponse(source, users.size());
    }

    private void upsertOrgAndMembership(
            User user,
            ExternalOrgUser external,
            String companyCodeNormalized,
            Map<String, OrgGroup> groupCache
    ) {
        String companyDisplayName = resolveCompanyDisplayName(external.companyName(), companyCodeNormalized);
        String divisionDisplayName = resolveOrDefault(external.divisionName(), "미지정 본부");
        String teamDisplayName = resolveOrDefault(external.teamName(), "미지정 팀");

        String companyCode = md5("COMPANY;" + companyCodeNormalized + ";" + companyDisplayName);
        OrgGroup companyGroup = upsertGroup(
                groupCache,
                "COMPANY",
                companyCode,
                companyDisplayName,
                null,
                companyCode
        );

        String divisionCode = md5("DIVISION;" + companyCode + ";" + divisionDisplayName);
        String divisionPath = companyCode + ";" + divisionCode;
        OrgGroup divisionGroup = upsertGroup(
                groupCache,
                "DIVISION",
                divisionCode,
                divisionDisplayName,
                companyCode,
                divisionPath
        );

        String teamCode = md5("TEAM;" + divisionCode + ";" + teamDisplayName);
        String teamPath = divisionPath + ";" + teamCode;
        OrgGroup teamGroup = upsertGroup(
                groupCache,
                "TEAM",
                teamCode,
                teamDisplayName,
                divisionCode,
                teamPath
        );

        upsertMembership(user.getId(), teamCode, "TEAM");

        String jobRank = trimOrNull(external.jobRank());
        if (jobRank != null) {
            String jobCode = md5("JOB_LEVEL;" + jobRank);
            OrgGroup jobGroup = upsertGroup(
                    groupCache,
                    "JOB_LEVEL",
                    jobCode,
                    jobRank,
                    null,
                    null
            );
            upsertMembership(user.getId(), jobCode, "JOB_LEVEL");
        }

        String dutyTitle = trimOrNull(external.dutyTitle());
        if (dutyTitle != null) {
            String dutyCode = md5("DUTY_TITLE;" + dutyTitle);
            OrgGroup dutyGroup = upsertGroup(
                    groupCache,
                    "DUTY_TITLE",
                    dutyCode,
                    dutyTitle,
                    null,
                    null
            );
            upsertMembership(user.getId(), dutyCode, "DUTY_TITLE");
        }
    }

    private void upsertMembership(Long userId, String groupCode, String memberGroupType) {
        OrgGroup group = orgGroupRepository.findByGroupTypeAndGroupCode(memberGroupType, groupCode)
                .orElseThrow(() -> new IllegalStateException("org_group not found: type=" + memberGroupType + ", code=" + groupCode));

        Optional<OrgGroupMember> existing = orgGroupMemberRepository.findByUser_IdAndMemberGroupType(userId, memberGroupType);
        if (existing.isPresent()) {
            OrgGroupMember m = existing.get();
            if (!m.getGroup().getGroupCode().equals(groupCode)) {
                m.setGroup(group);
                orgGroupMemberRepository.save(m);
            }
            return;
        }
        orgGroupMemberRepository.save(new OrgGroupMember(nullSafeUser(userId), group, memberGroupType));
    }

    private User nullSafeUser(Long userId) {
        // org_group_members.user_id FK는 JPA가 flush 시점에 참조하므로, user 엔티티가 필요하다.
        // 여기서는 findById를 한 번 수행한다.
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("user not found: " + userId));
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

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
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
