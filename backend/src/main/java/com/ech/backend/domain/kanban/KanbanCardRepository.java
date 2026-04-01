package com.ech.backend.domain.kanban;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KanbanCardRepository extends JpaRepository<KanbanCard, Long> {
    Optional<KanbanCard> findByIdAndColumn_Board_Id(Long cardId, Long boardId);

    /**
     * 보드 소속 카드와 담당자를 한 번에 로드한다.
     * PostgreSQL은 {@code SELECT DISTINCT}와 {@code ORDER BY}에 select list에 없는 표현을 허용하지 않아
     * JPQL에서 정렬을 제거하고, 보드 조립 시 서비스에서 컬럼·카드 {@code sortOrder} 기준으로 정렬한다.
     */
    @Query(
            "select distinct c from KanbanCard c join fetch c.assignees asn join fetch asn.user "
                    + "join c.column col where col.board.id = :boardId"
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
