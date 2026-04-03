package com.ech.backend.api.admin.user.dto;

import java.util.List;

public record OrgGroupOptionResponse(
        List<OrgGroupOption> teams,
        List<OrgGroupOption> jobLevels,
        List<OrgGroupOption> jobPositions,
        List<OrgGroupOption> jobTitles
) {
    public record OrgGroupOption(String groupCode, String displayName) {}
}
