package com.ech.backend.domain.aiassistant;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAiAssistantPreferenceRepository extends JpaRepository<UserAiAssistantPreference, String> {

    List<UserAiAssistantPreference> findByDigestModeIn(Collection<AiSuggestionDigestMode> modes);
}
