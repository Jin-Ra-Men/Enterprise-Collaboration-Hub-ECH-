package com.ech.backend.domain.kanban;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KanbanCardAssigneeRepository extends JpaRepository<KanbanCardAssignee, Long> {
    boolean existsByCard_IdAndUser_EmployeeNo(Long cardId, String employeeNo);

    Optional<KanbanCardAssignee> findByCard_IdAndUser_EmployeeNo(Long cardId, String employeeNo);

    /**
     * 사용자 삭제: 이 사용자가 담당자인 것 + 이 사용자가 만든 보드의 카드 담당자 전체 삭제.
     */
    @Modifying
    @Query(value = """
            DELETE FROM kanban_card_assignees
            WHERE user_id = :empNo
               OR card_id IN (
                   SELECT kc.id FROM kanban_cards kc
                   JOIN kanban_columns kcol ON kc.column_id = kcol.id
                   JOIN kanban_boards kb ON kcol.board_id = kb.id
                   WHERE kb.created_by = :empNo
               )
            """, nativeQuery = true)
    void deleteAllRelatedToEmployeeNo(@Param("empNo") String employeeNo);
}
