package com.ech.backend.domain.kanban;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KanbanColumnRepository extends JpaRepository<KanbanColumn, Long> {
    Optional<KanbanColumn> findByIdAndBoard_Id(Long columnId, Long boardId);

    List<KanbanColumn> findByBoard_IdOrderBySortOrderAsc(Long boardId);

    /** 사용자 삭제: 이 사용자가 만든 보드의 컬럼 전체 삭제 (kanban_boards 삭제 전 선행) */
    @Modifying
    @Query(value = "DELETE FROM kanban_columns WHERE board_id IN (SELECT id FROM kanban_boards WHERE created_by = :empNo)",
           nativeQuery = true)
    void deleteByBoardCreatorEmployeeNo(@Param("empNo") String employeeNo);
}
