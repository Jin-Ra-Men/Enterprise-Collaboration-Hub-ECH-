package com.ech.backend.domain.kanban;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KanbanCardAssigneeRepository extends JpaRepository<KanbanCardAssignee, Long> {
    boolean existsByCard_IdAndUser_Id(Long cardId, Long userId);

    Optional<KanbanCardAssignee> findByCard_IdAndUser_Id(Long cardId, Long userId);
}
