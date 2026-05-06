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

    @Query(value = """
            SELECT COUNT(*) FROM ai_suggestion_inbox a
            WHERE a.recipient_employee_no = :emp
              AND a.suggestion_kind = 'WORK_ITEM_HINT'
              AND a.channel_id = :channelId
              AND a.created_at > :since
              AND a.payload_json LIKE CONCAT('%\"sourceMessageId\":', CAST(:mid AS VARCHAR), '%')
            """, nativeQuery = true)
    long countRecentWorkItemHintForSourceMessage(
            @Param("emp") String recipientEmployeeNo,
            @Param("channelId") long channelId,
            @Param("mid") long sourceMessageId,
            @Param("since") OffsetDateTime since
    );
}
