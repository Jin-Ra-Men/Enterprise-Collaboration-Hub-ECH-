package com.ech.backend.domain.channel;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelReadStateRepository extends JpaRepository<ChannelReadState, Long> {
    Optional<ChannelReadState> findByChannel_IdAndUser_Id(Long channelId, Long userId);
}
