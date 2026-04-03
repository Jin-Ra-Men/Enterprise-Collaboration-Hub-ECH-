package com.ech.backend.domain.channel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
    boolean existsByChannelIdAndUserEmployeeNo(Long channelId, String employeeNo);

    Optional<ChannelMember> findByChannel_IdAndUser_EmployeeNo(Long channelId, String employeeNo);

    List<ChannelMember> findByChannelId(Long channelId);

    @Query("SELECT cm FROM ChannelMember cm JOIN FETCH cm.user WHERE cm.channel.id = :channelId ORDER BY cm.joinedAt ASC")
    List<ChannelMember> findByChannelIdFetchUsers(@Param("channelId") Long channelId);

    @Query("SELECT u.employeeNo FROM ChannelMember cm JOIN cm.user u WHERE cm.channel.id = :channelId AND u.employeeNo IN :employeeNos")
    List<String> findMemberEmployeeNosInChannel(
            @Param("channelId") Long channelId, @Param("employeeNos") Collection<String> employeeNos);

    /** 사용자 삭제: 이 사용자의 채널 멤버 + 이 사용자가 참여한 다른 채널 멤버 전체 삭제 */
    @Modifying
    @Query(value = """
            DELETE FROM channel_members
            WHERE channel_id IN (SELECT id FROM channels WHERE created_by = :empNo)
               OR user_id = :empNo
            """, nativeQuery = true)
    void deleteAllRelatedToEmployeeNo(@Param("empNo") String employeeNo);
}
