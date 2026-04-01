package com.ech.backend.domain.work;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemRepository extends JpaRepository<WorkItem, Long> {

    Optional<WorkItem> findBySourceMessage_Id(Long messageId);
    List<WorkItem> findBySourceChannel_IdOrderByCreatedAtDesc(Long channelId, Pageable pageable);

    /**
     * 통합 검색: 업무 제목 또는 설명에서 키워드를 검색한다 (워크스페이스 전체 대상).
     */
    @Query("""
            SELECT w FROM WorkItem w
            JOIN FETCH w.sourceChannel sc
            WHERE LOWER(w.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR (w.description IS NOT NULL
                   AND LOWER(w.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY w.createdAt DESC
            """)
    List<WorkItem> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
