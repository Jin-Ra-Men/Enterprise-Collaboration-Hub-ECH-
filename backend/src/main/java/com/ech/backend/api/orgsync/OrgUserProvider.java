package com.ech.backend.api.orgsync;

import com.ech.backend.api.orgsync.dto.ExternalOrgUser;
import com.ech.backend.api.orgsync.dto.OrgSyncSource;
import java.util.List;

public interface OrgUserProvider {
    OrgSyncSource source();
    List<ExternalOrgUser> fetchUsers();
}
