package com.ech.backend.domain.work;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemRepository extends JpaRepository<WorkItem, Long> {

    Optional<WorkItem> findBySourceMessage_Id(Long messageId);
    List<WorkItem> findBySourceChannel_IdOrderByCreatedAtDesc(Long channelId, Pageable pageable);

    /**
     * 사이드바: 내가 칸반 카드 담당으로 지정된 업무 항목(중복 제거).
     */
    @Query(
            "select distinct w from WorkItem w "
                    + "join fetch w.sourceChannel ch "
                    + "join w.kanbanCards c "
                    + "join c.assignees a "
                    + "where a.user.employeeNo = :emp"
    )
    List<WorkItem> findDistinctWithMyCardAssignment(@Param("emp") String employeeNo);

    @Query(
            """
                    select w from WorkItem w
                    join fetch w.sourceChannel ch
                    join fetch w.createdBy cb
                    left join fetch w.sourceMessage sm
                    where w.inUse = true
                      and w.status <> 'DONE'
                      and w.dueAt is not null
                      and w.dueAt < :startOfToday
                      and exists (
                          select 1 from ChannelMember cm
                          where cm.channel.id = ch.id and cm.user.employeeNo = :emp
                      )
                    order by w.dueAt asc
                    """
    )
    List<WorkItem> findOverdueForMember(
            @Param("emp") String employeeNo,
            @Param("startOfToday") OffsetDateTime startOfToday,
            Pageable pageable);

    @Query(
            """
                    select w from WorkItem w
                    join fetch w.sourceChannel ch
                    join fetch w.createdBy cb
                    left join fetch w.sourceMessage sm
                    where w.inUse = true
                      and w.status <> 'DONE'
                      and w.dueAt is not null
                      and w.dueAt >= :startInclusive
                      and w.dueAt < :endExclusive
                      and exists (
                          select 1 from ChannelMember cm
                          where cm.channel.id = ch.id and cm.user.employeeNo = :emp
                      )
                    order by w.dueAt asc
                    """
    )
    List<WorkItem> findDueTodayForMember(
            @Param("emp") String employeeNo,
            @Param("startInclusive") OffsetDateTime startInclusive,
            @Param("endExclusive") OffsetDateTime endExclusive,
            Pageable pageable);

    /**
     * 원본 메시지 본문에 @{사번|…} 또는 @{사번} 멘션 토큰이 포함된 업무(채널 멤버만).
     */
    @Query(
            """
                    select distinct w from WorkItem w
                    join fetch w.sourceChannel ch
                    join fetch w.createdBy cb
                    join fetch w.sourceMessage sm
                    where w.inUse = true
                      and w.status <> 'DONE'
                      and sm.isDeleted = false
                      and (
                          sm.body like concat('@{', :emp, '|%')
                          or sm.body like concat('@{', :emp, '}')
                      )
                      and exists (
                          select 1 from ChannelMember cm
                          where cm.channel.id = ch.id and cm.user.employeeNo = :emp
                      )
                    order by w.updatedAt desc
                    """
    )
    List<WorkItem> findMentionLinkedForMember(@Param("emp") String employeeNo, Pageable pageable);

    /**
     * 통합 검색: 업무 제목 또는 설명에서 키워드를 검색한다 (워크스페이스 전체 대상).
     */
    @Query("""
            SELECT w FROM WorkItem w
            JOIN FETCH w.sourceChannel sc
            WHERE LOWER(w.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR (w.description IS NOT NULL
                   AND LOWER(w.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY w.createdAt DESC
            """)
    List<WorkItem> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /** 사용자 삭제: 해당 사용자가 생성한 work_item 삭제 */
    @Modifying
    @Query(value = "DELETE FROM work_items WHERE created_by = :empNo", nativeQuery = true)
    void deleteByCreatorEmployeeNo(@Param("empNo") String employeeNo);

    /**
     * 사용자 삭제: 삭제 대상 채널에 연결된 work_item 삭제
     * (channels.created_by 기준, work_items에 ON DELETE CASCADE가 없으므로 선제 삭제 필요)
     */
    @Modifying
    @Query(value = """
            DELETE FROM work_items
            WHERE source_channel_id IN (
                SELECT id FROM channels WHERE created_by = :empNo
            )
            """, nativeQuery = true)
    void deleteBySourceChannelCreatorEmployeeNo(@Param("empNo") String employeeNo);
}
