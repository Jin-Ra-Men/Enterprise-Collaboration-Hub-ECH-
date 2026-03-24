package com.ech.backend.domain.kanban;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KanbanCardRepository extends JpaRepository<KanbanCard, Long> {
    Optional<KanbanCard> findByIdAndColumn_Board_Id(Long cardId, Long boardId);

    @Query(
            "select distinct c from KanbanCard c join fetch c.assignees asn join fetch asn.user "
                    + "join c.column col where col.board.id = :boardId order by col.sortOrder asc, c.sortOrder asc"
    )
    List<KanbanCard> findAllForBoardWithAssignees(@Param("boardId") Long boardId);

    /**
     * 통합 검색: 칸반 카드 제목 또는 설명에서 키워드를 검색한다 (워크스페이스 전체 대상).
     */
    @Query("""
            SELECT c FROM KanbanCard c
            JOIN FETCH c.column col
            JOIN FETCH col.board board
            WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR (c.description IS NOT NULL
                   AND LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY c.createdAt DESC
            """)
    List<KanbanCard> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
