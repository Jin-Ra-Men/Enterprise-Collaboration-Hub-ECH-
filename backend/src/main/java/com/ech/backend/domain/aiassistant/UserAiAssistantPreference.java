package com.ech.backend.domain.aiassistant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_ai_assistant_preferences")
public class UserAiAssistantPreference {

    @Id
    @Column(name = "employee_no", nullable = false, length = 50)
    private String employeeNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "proactive_tone", nullable = false, length = 20)
    private AiAssistantTone proactiveTone = AiAssistantTone.BALANCED;

    @Enumerated(EnumType.STRING)
    @Column(name = "digest_mode", nullable = false, length = 20)
    private AiSuggestionDigestMode digestMode = AiSuggestionDigestMode.REALTIME;

    /** After dismissing a proactive suggestion, enqueue cooldown until this instant (Phase 7-3-2). */
    @Column(name = "proactive_cooldown_until")
    private OffsetDateTime proactiveCooldownUntil;

    /** When false, the user must not invoke gateway AI, proactive inbox, or AI-origin calendar suggestions. */
    @Column(name = "ai_assistant_enabled", nullable = false)
    private boolean aiAssistantEnabled = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected UserAiAssistantPreference() {
    }

    public UserAiAssistantPreference(String employeeNo) {
        this.employeeNo = employeeNo;
        touch();
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public AiAssistantTone getProactiveTone() {
        return proactiveTone;
    }

    public void setProactiveTone(AiAssistantTone proactiveTone) {
        if (proactiveTone != null) {
            this.proactiveTone = proactiveTone;
            touch();
        }
    }

    public AiSuggestionDigestMode getDigestMode() {
        return digestMode;
    }

    public void setDigestMode(AiSuggestionDigestMode digestMode) {
        if (digestMode != null) {
            this.digestMode = digestMode;
            touch();
        }
    }

    public OffsetDateTime getProactiveCooldownUntil() {
        return proactiveCooldownUntil;
    }

    public void setProactiveCooldownUntil(OffsetDateTime proactiveCooldownUntil) {
        this.proactiveCooldownUntil = proactiveCooldownUntil;
        touch();
    }

    public boolean isAiAssistantEnabled() {
        return aiAssistantEnabled;
    }

    public void setAiAssistantEnabled(boolean aiAssistantEnabled) {
        this.aiAssistantEnabled = aiAssistantEnabled;
        touch();
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
