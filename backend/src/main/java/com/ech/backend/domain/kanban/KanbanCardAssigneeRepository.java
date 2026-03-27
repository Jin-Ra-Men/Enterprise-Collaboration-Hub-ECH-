package com.ech.backend.domain.kanban;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KanbanCardAssigneeRepository extends JpaRepository<KanbanCardAssignee, Long> {
    boolean existsByCard_IdAndUser_EmployeeNo(Long cardId, String employeeNo);

    Optional<KanbanCardAssignee> findByCard_IdAndUser_EmployeeNo(Long cardId, String employeeNo);
}
