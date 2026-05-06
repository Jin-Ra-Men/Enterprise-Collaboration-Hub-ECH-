package com.ech.backend.domain.work;

import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.kanban.KanbanCard;
import com.ech.backend.domain.message.Message;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "work_items")
public class WorkItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String status = "OPEN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_message_id")
    private Message sourceMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_channel_id", nullable = false)
    private Channel sourceChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "employee_no", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'NORMAL'")
    @Column(name = "priority", nullable = false, length = 20)
    private WorkItemPriority priority = WorkItemPriority.NORMAL;

    /**
     * {@code false} when soft-deleted (hidden / grey in UI); restore sets back to {@code true}.
     * {@link ColumnDefault} so ddl-auto can add NOT NULL to existing rows (PostgreSQL needs DEFAULT on add).
     */
    @ColumnDefault("true")
    @Column(name = "in_use", nullable = false)
    private boolean inUse = true;

    @OneToMany(mappedBy = "workItem")
    private List<KanbanCard> kanbanCards = new ArrayList<>();

    protected WorkItem() {
    }

    public WorkItem(
            String title,
            String description,
            String status,
            Message sourceMessage,
            Channel sourceChannel,
            User createdBy
    ) {
        this(title, description, status, sourceMessage, sourceChannel, createdBy, null, WorkItemPriority.NORMAL);
    }

    public WorkItem(
            String title,
            String description,
            String status,
            Message sourceMessage,
            Channel sourceChannel,
            User createdBy,
            OffsetDateTime dueAt,
            WorkItemPriority priority
    ) {
        this.title = title;
        this.description = description;
        this.status = status != null && !status.isBlank() ? status : "OPEN";
        this.sourceMessage = sourceMessage;
        this.sourceChannel = sourceChannel;
        this.createdBy = createdBy;
        this.dueAt = dueAt;
        this.priority = priority != null ? priority : WorkItemPriority.NORMAL;
        this.inUse = true;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public Message getSourceMessage() {
        return sourceMessage;
    }

    public Channel getSourceChannel() {
        return sourceChannel;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDueAt() {
        return dueAt;
    }

    public WorkItemPriority getPriority() {
        return priority;
    }

    public void setDueAt(OffsetDateTime dueAt) {
        this.dueAt = dueAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public void clearDueAt() {
        this.dueAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void setPriority(WorkItemPriority priority) {
        if (priority != null) {
            this.priority = priority;
            this.updatedAt = OffsetDateTime.now();
        }
    }

    public void update(String title, String description, String status) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (description != null) {
            this.description = description.trim().isEmpty() ? null : description.trim();
        }
        if (status != null && !status.isBlank()) {
            this.status = status.trim();
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
        this.updatedAt = OffsetDateTime.now();
    }

    public List<KanbanCard> getKanbanCards() {
        return kanbanCards;
    }
}
