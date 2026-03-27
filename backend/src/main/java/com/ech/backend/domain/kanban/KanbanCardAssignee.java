package com.ech.backend.domain.kanban;

import com.ech.backend.domain.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "kanban_card_assignees",
        uniqueConstraints = @UniqueConstraint(columnNames = {"card_id", "user_id"})
)
public class KanbanCardAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private KanbanCard card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "employee_no", nullable = false)
    private User user;

    protected KanbanCardAssignee() {
    }

    public KanbanCardAssignee(KanbanCard card, User user) {
        this.card = card;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public KanbanCard getCard() {
        return card;
    }

    public User getUser() {
        return user;
    }
}
