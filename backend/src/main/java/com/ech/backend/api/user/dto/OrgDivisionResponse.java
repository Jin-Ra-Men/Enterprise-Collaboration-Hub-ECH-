package com.ech.backend.api.user.dto;

import java.util.List;

public record OrgDivisionResponse(String name, List<OrgTeamResponse> teams) {}
