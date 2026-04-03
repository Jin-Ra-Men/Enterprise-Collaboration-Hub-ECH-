package com.ech.backend.domain.kanban;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KanbanCardEventRepository extends JpaRepository<KanbanCardEvent, Long> {
    List<KanbanCardEvent> findByCard_IdOrderByCreatedAtDesc(Long cardId, Pageable pageable);

    /**
     * 사용자 삭제: 이 사용자가 actor인 이벤트 + 이 사용자가 만든 보드의 카드 이벤트 전체 삭제.
     * kanban_card_events.card_id → kanban_cards, actor_user_id → users 모두 FK RESTRICT 대응.
     */
    @Modifying
    @Query(value = """
            DELETE FROM kanban_card_events
            WHERE actor_user_id = :empNo
               OR card_id IN (
                   SELECT kc.id FROM kanban_cards kc
                   JOIN kanban_columns kcol ON kc.column_id = kcol.id
                   JOIN kanban_boards kb ON kcol.board_id = kb.id
                   WHERE kb.created_by = :empNo
               )
            """, nativeQuery = true)
    void deleteAllRelatedToEmployeeNo(@Param("empNo") String employeeNo);
}
