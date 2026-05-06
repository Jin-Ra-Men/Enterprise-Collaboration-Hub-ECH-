package com.ech.backend.domain.aiassistant;

import com.ech.backend.domain.channel.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_suggestion_inbox")
public class AiSuggestionInboxItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_employee_no", nullable = false, length = 50)
    private String recipientEmployeeNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggestion_kind", nullable = false, length = 40)
    private AiSuggestionKind suggestionKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiSuggestionInboxStatus status = AiSuggestionInboxStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    private Double confidence;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected AiSuggestionInboxItem() {
    }

    public AiSuggestionInboxItem(
            String recipientEmployeeNo,
            AiSuggestionKind suggestionKind,
            Channel channel,
            String title,
            String summary,
            String payloadJson,
            Double confidence
    ) {
        this.recipientEmployeeNo = recipientEmployeeNo;
        this.suggestionKind = suggestionKind;
        this.channel = channel;
        this.title = title;
        this.summary = summary;
        this.payloadJson = payloadJson;
        this.confidence = confidence;
        touchTimes();
    }

    @PrePersist
    @PreUpdate
    void touchTimes() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getRecipientEmployeeNo() {
        return recipientEmployeeNo;
    }

    public AiSuggestionKind getSuggestionKind() {
        return suggestionKind;
    }

    public AiSuggestionInboxStatus getStatus() {
        return status;
    }

    public void setStatus(AiSuggestionInboxStatus status) {
        this.status = status;
        touchTimes();
    }

    public Channel getChannel() {
        return channel;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Double getConfidence() {
        return confidence;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
