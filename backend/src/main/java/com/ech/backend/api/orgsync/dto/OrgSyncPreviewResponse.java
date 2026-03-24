package com.ech.backend.api.orgsync.dto;

import java.util.List;

public record OrgSyncPreviewResponse(
        OrgSyncSource source,
        int count,
        List<ExternalOrgUser> users
) {
}
