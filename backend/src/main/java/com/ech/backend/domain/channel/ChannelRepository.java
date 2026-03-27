package com.ech.backend.domain.channel;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
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
}
