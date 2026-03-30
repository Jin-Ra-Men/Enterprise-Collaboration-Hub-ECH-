package com.ech.backend.domain.channel;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
    boolean existsByChannelIdAndUserEmployeeNo(Long channelId, String employeeNo);

    Optional<ChannelMember> findByChannel_IdAndUser_EmployeeNo(Long channelId, String employeeNo);

    List<ChannelMember> findByChannelId(Long channelId);

    @Query("SELECT cm FROM ChannelMember cm JOIN FETCH cm.user WHERE cm.channel.id = :channelId ORDER BY cm.joinedAt ASC")
    List<ChannelMember> findByChannelIdFetchUsers(@Param("channelId") Long channelId);
}
