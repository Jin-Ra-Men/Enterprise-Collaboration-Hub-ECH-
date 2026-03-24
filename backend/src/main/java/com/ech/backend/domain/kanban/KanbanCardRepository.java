package com.ech.backend.domain.kanban;

import java.util.List;
import java.util.Optional;
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
}
