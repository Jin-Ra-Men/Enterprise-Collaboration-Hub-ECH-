package com.ech.backend.domain.aiassistant;

import com.ech.backend.domain.channel.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "channel_ai_assistant_preferences")
public class ChannelAiAssistantPreference {

    @Id
    @Column(name = "channel_id")
    private Long channelId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", insertable = false, updatable = false)
    private Channel channel;

    @Column(name = "proactive_opt_in", nullable = false)
    private boolean proactiveOptIn;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected ChannelAiAssistantPreference() {
    }

    public ChannelAiAssistantPreference(Long channelId, boolean proactiveOptIn) {
        this.channelId = channelId;
        this.proactiveOptIn = proactiveOptIn;
        touch();
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getChannelId() {
        return channelId;
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isProactiveOptIn() {
        return proactiveOptIn;
    }

    public void setProactiveOptIn(boolean proactiveOptIn) {
        this.proactiveOptIn = proactiveOptIn;
        touch();
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
