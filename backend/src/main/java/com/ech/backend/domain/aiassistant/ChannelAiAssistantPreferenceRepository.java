package com.ech.backend.domain.aiassistant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelAiAssistantPreferenceRepository extends JpaRepository<ChannelAiAssistantPreference, Long> {

    Optional<ChannelAiAssistantPreference> findByChannelId(Long channelId);

    List<ChannelAiAssistantPreference> findByProactiveOptInTrue();
}
