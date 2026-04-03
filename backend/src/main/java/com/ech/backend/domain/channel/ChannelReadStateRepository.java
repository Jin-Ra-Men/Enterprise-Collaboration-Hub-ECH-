package com.ech.backend.domain.channel;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelReadStateRepository extends JpaRepository<ChannelReadState, Long> {
    Optional<ChannelReadState> findByChannel_IdAndUser_EmployeeNo(Long channelId, String employeeNo);

    void deleteByChannel_IdAndUser_EmployeeNo(Long channelId, String employeeNo);

    /** 사용자 삭제: last_read_message_id NULL 초기화 (messages 삭제 전 선행) */
    @Modifying
    @Query(value = """
            UPDATE channel_read_states SET last_read_message_id = NULL
            WHERE last_read_message_id IN (SELECT id FROM messages WHERE sender_id = :empNo)
               OR channel_id IN (SELECT id FROM channels WHERE created_by = :empNo)
            """, nativeQuery = true)
    void nullLastReadRefByEmployeeNo(@Param("empNo") String employeeNo);

    /** 사용자 삭제: 이 사용자의 채널 + 이 사용자의 읽음 상태 전체 삭제 */
    @Modifying
    @Query(value = """
            DELETE FROM channel_read_states
            WHERE channel_id IN (SELECT id FROM channels WHERE created_by = :empNo)
               OR user_id = :empNo
            """, nativeQuery = true)
    void deleteAllRelatedToEmployeeNo(@Param("empNo") String employeeNo);
}
