package com.ech.backend.api.orgsync;

import com.ech.backend.api.orgsync.dto.ExternalOrgUser;
import com.ech.backend.api.orgsync.dto.OrgSyncPreviewResponse;
import com.ech.backend.api.orgsync.dto.OrgSyncResultResponse;
import com.ech.backend.api.orgsync.dto.OrgSyncSource;
import com.ech.backend.domain.user.UserRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrgSyncService {

    private final UserRepository userRepository;
    private final Map<OrgSyncSource, OrgUserProvider> providerBySource;

    public OrgSyncService(UserRepository userRepository, List<OrgUserProvider> providers) {
        this.userRepository = userRepository;
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
        for (ExternalOrgUser user : users) {
            userRepository.upsertByEmployeeNo(
                    user.employeeNo(),
                    user.email(),
                    user.name(),
                    user.department(),
                    safeRole(user.role()),
                    safeStatus(user.status())
            );
        }
        return new OrgSyncResultResponse(source, users.size());
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
