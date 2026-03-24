package com.ech.backend.domain.audit;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
              AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
              AND (:eventType IS NULL OR a.eventType = :eventType)
              AND (:resourceType IS NULL OR a.resourceType = :resourceType)
              AND (:workspaceKey IS NULL OR a.workspaceKey = :workspaceKey)
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> search(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("actorUserId") Long actorUserId,
            @Param("eventType") AuditEventType eventType,
            @Param("resourceType") String resourceType,
            @Param("workspaceKey") String workspaceKey,
            Pageable pageable
    );

    /** 보존 정책 적용: 지정 일시 이전의 감사 로그를 물리 삭제. */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
