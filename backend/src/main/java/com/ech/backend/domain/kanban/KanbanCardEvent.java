package com.ech.backend.domain.kanban;

import com.ech.backend.domain.user.User;
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
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "kanban_card_events")
public class KanbanCardEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private KanbanCard card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private KanbanCardEventType eventType;

    @Column(name = "from_ref", length = 500)
    private String fromRef;

    @Column(name = "to_ref", length = 500)
    private String toRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected KanbanCardEvent() {
    }

    public KanbanCardEvent(
            KanbanCard card,
            User actor,
            KanbanCardEventType eventType,
            String fromRef,
            String toRef
    ) {
        this.card = card;
        this.actor = actor;
        this.eventType = eventType;
        this.fromRef = fromRef;
        this.toRef = toRef;
    }

    public Long getId() {
        return id;
    }

    public KanbanCard getCard() {
        return card;
    }

    public User getActor() {
        return actor;
    }

    public KanbanCardEventType getEventType() {
        return eventType;
    }

    public String getFromRef() {
        return fromRef;
    }

    public String getToRef() {
        return toRef;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
