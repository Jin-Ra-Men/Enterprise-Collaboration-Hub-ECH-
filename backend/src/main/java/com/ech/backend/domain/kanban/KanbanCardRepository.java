package com.ech.backend.domain.kanban;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KanbanCardRepository extends JpaRepository<KanbanCard, Long> {
    Optional<KanbanCard> findByIdAndColumn_Board_Id(Long cardId, Long boardId);

    List<KanbanCard> findByWorkItem_Id(Long workItemId);

    /**
     * 보드 소속 카드와 담당자를 한 번에 로드한다.
     * 담당자가 없는 카드도 포함해야 하므로 {@code assignees}/{@code user}는 {@code LEFT JOIN FETCH}로 연결한다
     * ({@code JOIN FETCH}만 쓰면 INNER JOIN이 되어 assignee 0건 카드가 결과에서 빠짐).
     * PostgreSQL은 {@code SELECT DISTINCT}와 {@code ORDER BY}에 select list에 없는 표현을 허용하지 않아
     * 정렬은 서비스에서 컬럼·카드 {@code sortOrder} 기준으로 수행한다.
     */
    @Query(
            "select distinct c from KanbanCard c "
                    + "join fetch c.column col "
                    + "left join fetch c.workItem wi "
                    + "left join fetch c.assignees asn "
                    + "left join fetch asn.user "
                    + "where col.board.id = :boardId"
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

    @Query("""
            SELECT DISTINCT c FROM KanbanCard c
            JOIN c.assignees asn
            JOIN asn.user u
            JOIN FETCH c.column col
            JOIN FETCH col.board board
            JOIN FETCH board.sourceChannel ch
            WHERE u.employeeNo = :employeeNo
            ORDER BY c.updatedAt DESC
            """)
    List<KanbanCard> findAssignedChannelCards(@Param("employeeNo") String employeeNo, Pageable pageable);
}
