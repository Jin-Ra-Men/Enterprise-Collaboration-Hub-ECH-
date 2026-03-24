package com.ech.backend.domain.channel;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
    boolean existsByChannelIdAndUserId(Long channelId, Long userId);
    List<ChannelMember> findByChannelId(Long channelId);
}
