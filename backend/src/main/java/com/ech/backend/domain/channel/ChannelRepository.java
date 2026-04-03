package com.ech.backend.domain.channel;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    @Query("SELECT c FROM Channel c JOIN FETCH c.createdBy WHERE c.id = :id")
    Optional<Channel> findByIdWithCreatedBy(@Param("id") Long id);

    Optional<Channel> findByWorkspaceKeyAndName(String workspaceKey, String name);

    @Query("""
            SELECT c FROM Channel c
            WHERE EXISTS (
                SELECT cm FROM ChannelMember cm
                WHERE cm.channel.id = c.id AND cm.user.employeeNo = :employeeNo
            )
            ORDER BY c.createdAt DESC
            """)
    List<Channel> findByMemberEmployeeNo(@Param("employeeNo") String employeeNo);

    /**
     * 통합 검색: 사용자가 속한 채널 중 채널명/설명 키워드 검색.
     */
    @Query("""
            SELECT c FROM Channel c
            WHERE EXISTS (
                SELECT cm FROM ChannelMember cm
                WHERE cm.channel.id = c.id AND cm.user.employeeNo = :employeeNo
            )
              AND LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY c.createdAt DESC
            """)
    List<Channel> searchByKeywordInJoinedChannels(@Param("keyword") String keyword,
                                                  @Param("employeeNo") String employeeNo,
                                                  Pageable pageable);

    /** 사용자 삭제: 해당 사용자가 생성한 채널 전체 삭제 (멤버·메시지·읽음상태·파일 CASCADE) */
    @Modifying
    @Query(value = "DELETE FROM channels WHERE created_by = :empNo", nativeQuery = true)
    void deleteByCreatorEmployeeNo(@Param("empNo") String employeeNo);
}
