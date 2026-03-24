package com.ech.backend.domain.work;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemRepository extends JpaRepository<WorkItem, Long> {
    Optional<WorkItem> findBySourceMessage_Id(Long messageId);
}
