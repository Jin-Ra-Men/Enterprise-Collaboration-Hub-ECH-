package com.ech.backend.api.work.dto;

import java.util.List;

/** Sidebar «내 할 일»: 마감·멘션 연계·담당 칸반 버킷 + 마감 배지 집계. */
public record MyWorkTodosResponse(
        List<WorkItemSidebarResponse> overdue,
        List<WorkItemSidebarResponse> dueToday,
        List<WorkItemSidebarResponse> mentionLinked,
        List<WorkItemSidebarResponse> kanbanAssigned,
        TodoBadgeCounts badgeCounts
) {
}
