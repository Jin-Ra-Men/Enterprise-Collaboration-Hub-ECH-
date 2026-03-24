package com.ech.backend.domain.kanban;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KanbanColumnRepository extends JpaRepository<KanbanColumn, Long> {
    Optional<KanbanColumn> findByIdAndBoard_Id(Long columnId, Long boardId);

    List<KanbanColumn> findByBoard_IdOrderBySortOrderAsc(Long boardId);
}
