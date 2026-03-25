package com.ech.backend.api.user.dto;

import java.util.List;

public record OrgCompanyResponse(String name, List<OrgDivisionResponse> divisions) {}
