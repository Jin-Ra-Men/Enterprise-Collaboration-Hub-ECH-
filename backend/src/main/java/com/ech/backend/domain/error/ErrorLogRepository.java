package com.ech.backend.domain.error;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {
    @Query("""
            SELECT e FROM ErrorLog e
            WHERE (:from IS NULL OR e.createdAt >= :from)
              AND (:to IS NULL OR e.createdAt <= :to)
              AND (:errorCode IS NULL OR e.errorCode = :errorCode)
              AND (:pathKeyword IS NULL OR LOWER(e.path) LIKE LOWER(CONCAT('%', :pathKeyword, '%')))
            ORDER BY e.createdAt DESC
            """)
    List<ErrorLog> search(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("errorCode") String errorCode,
            @Param("pathKeyword") String pathKeyword,
            Pageable pageable
    );
}
