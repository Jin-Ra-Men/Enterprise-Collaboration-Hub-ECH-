package com.ech.backend.domain.kanban;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KanbanCardEventRepository extends JpaRepository<KanbanCardEvent, Long> {
    List<KanbanCardEvent> findByCard_IdOrderByCreatedAtDesc(Long cardId, Pageable pageable);

    /** 사용자 삭제 전: 해당 사용자가 actor인 이벤트 삭제 (FK 제약 해소) */
    @Modifying
    @Query(value = "DELETE FROM kanban_card_events WHERE actor_user_id = :empNo", nativeQuery = true)
    void deleteByActorEmployeeNo(@Param("empNo") String employeeNo);
}
