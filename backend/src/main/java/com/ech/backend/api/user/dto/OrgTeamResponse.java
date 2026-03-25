package com.ech.backend.api.user.dto;

import java.util.List;

public record OrgTeamResponse(String name, List<UserSearchResponse> users) {}
