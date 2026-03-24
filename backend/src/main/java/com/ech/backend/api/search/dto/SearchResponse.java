package com.ech.backend.api.search.dto;

import java.util.List;

public record SearchResponse(
        String query,
        String type,
        int totalCount,
        List<SearchResultItem> items
) {
}
