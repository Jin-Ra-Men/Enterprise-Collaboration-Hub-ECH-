package com.ech.backend.domain.kanban;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KanbanBoardRepository extends JpaRepository<KanbanBoard, Long> {
    Optional<KanbanBoard> findByWorkspaceKeyAndName(String workspaceKey, String name);
    Optional<KanbanBoard> findByWorkspaceKeyAndSourceChannel_Id(String workspaceKey, Long channelId);

    List<KanbanBoard> findByWorkspaceKeyOrderByCreatedAtDesc(String workspaceKey, Pageable pageable);
}
