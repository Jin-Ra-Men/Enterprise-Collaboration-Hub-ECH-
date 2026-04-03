package com.ech.backend.domain.kanban;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KanbanBoardRepository extends JpaRepository<KanbanBoard, Long> {
    Optional<KanbanBoard> findByWorkspaceKeyAndName(String workspaceKey, String name);
    Optional<KanbanBoard> findByWorkspaceKeyAndSourceChannel_Id(String workspaceKey, Long channelId);

    List<KanbanBoard> findByWorkspaceKeyOrderByCreatedAtDesc(String workspaceKey, Pageable pageable);

    /** 사용자 삭제: 해당 사용자가 생성한 칸반 보드 전체 삭제 (컬럼→카드→담당자·이벤트 CASCADE) */
    @Modifying
    @Query(value = "DELETE FROM kanban_boards WHERE created_by = :empNo", nativeQuery = true)
    void deleteByCreatorEmployeeNo(@Param("empNo") String employeeNo);
}
