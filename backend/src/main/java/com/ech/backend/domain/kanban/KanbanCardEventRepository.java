package com.ech.backend.domain.kanban;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KanbanCardEventRepository extends JpaRepository<KanbanCardEvent, Long> {
    List<KanbanCardEvent> findByCard_IdOrderByCreatedAtDesc(Long cardId, Pageable pageable);
}
