package com.ech.backend.domain.aiassistant;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiSuggestionInboxRepository extends JpaRepository<AiSuggestionInboxItem, Long> {

    @Query("SELECT COUNT(a) FROM AiSuggestionInboxItem a WHERE a.channel IS NOT NULL AND a.channel.id = :channelId AND a.createdAt > :since")
    long countCreatedAfterForChannel(@Param("channelId") Long channelId, @Param("since") OffsetDateTime since);

    List<AiSuggestionInboxItem> findTop50ByRecipientEmployeeNoAndStatusOrderByCreatedAtDesc(
            String recipientEmployeeNo,
            AiSuggestionInboxStatus status
    );

    Optional<AiSuggestionInboxItem> findByIdAndRecipientEmployeeNo(Long id, String recipientEmployeeNo);

    long countByRecipientEmployeeNoAndSuggestionKindAndCreatedAtAfter(
            String recipientEmployeeNo,
            AiSuggestionKind suggestionKind,
            OffsetDateTime since);

    long countByRecipientEmployeeNoAndChannel_IdAndSuggestionKindAndCreatedAtAfter(
            String recipientEmployeeNo,
            Long channelId,
            AiSuggestionKind suggestionKind,
            OffsetDateTime since);
}
