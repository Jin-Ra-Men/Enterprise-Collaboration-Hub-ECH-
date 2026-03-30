package com.ech.backend.domain.error;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {
    @Query("""
            SELECT e FROM ErrorLog e
            WHERE (:from IS NULL OR e.createdAt >= :from)
              AND (:to IS NULL OR e.createdAt <= :to)
              AND (:errorCode IS NULL OR e.errorCode = :errorCode)
            ORDER BY e.createdAt DESC
            """)
    List<ErrorLog> search(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("errorCode") String errorCode,
            Pageable pageable
    );

    /** 보존 정책 적용: 지정 일시 이전의 오류 로그를 물리 삭제. */
    @Modifying
    @Query("DELETE FROM ErrorLog e WHERE e.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
