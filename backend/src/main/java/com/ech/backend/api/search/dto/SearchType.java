package com.ech.backend.api.search.dto;

/**
 * 통합 검색 대상 자원 유형.
 * ALL 선택 시 모든 유형을 검색한다.
 */
public enum SearchType {
    ALL,
    MESSAGES,
    COMMENTS,
    CHANNELS,
    FILES,
    WORK_ITEMS,
    KANBAN_CARDS
}
