package com.ech.backend.api.orgsync.dto;

public record OrgSyncResultResponse(
        OrgSyncSource source,
        int syncedCount
) {
}
