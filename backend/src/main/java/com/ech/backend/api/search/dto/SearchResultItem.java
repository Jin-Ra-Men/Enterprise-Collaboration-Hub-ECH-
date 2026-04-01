package com.ech.backend.api.search.dto;

import java.time.OffsetDateTime;

/**
 * 통합 검색 결과 항목 1건.
 *
 * @param type        자원 유형 (MESSAGE / FILE / WORK_ITEM / KANBAN_CARD)
 * @param id          자원 ID
 * @param title       표시 제목 (파일명, 업무 제목, 카드 제목, 메시지 미리보기)
 * @param preview     내용 미리보기 (최대 150자, 대화 본문은 감사 정책에 따라 포함)
 * @param contextId          채널 ID (메시지/파일) 또는 보드 ID (칸반)
 * @param contextName        채널명 또는 보드명
 * @param createdAt          생성 일시
 * @param threadRootMessageId 댓글(`COMMENT`) 검색 시 스레드 원글(루트) 메시지 ID — 그 외 타입은 {@code null}
 */
public record SearchResultItem(
        String type,
        Long id,
        String title,
        String preview,
        Long contextId,
        String contextName,
        OffsetDateTime createdAt,
        Long threadRootMessageId
) {
    /** preview 문자열을 안전하게 잘라 반환한다. */
    public static String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
