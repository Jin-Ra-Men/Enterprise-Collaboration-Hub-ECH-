package com.ech.backend.domain.kanban;

import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "kanban_boards",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_key", "name"})
)
public class KanbanBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_key", nullable = false, length = 100)
    private String workspaceKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_channel_id")
    private Channel sourceChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "employee_no", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<KanbanColumn> columns = new ArrayList<>();

    protected KanbanBoard() {
    }

    public KanbanBoard(String workspaceKey, String name, String description, User createdBy) {
        this(workspaceKey, name, description, null, createdBy);
    }

    public KanbanBoard(String workspaceKey, String name, String description, Channel sourceChannel, User createdBy) {
        this.workspaceKey = workspaceKey;
        this.name = name;
        this.description = description;
        this.sourceChannel = sourceChannel;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getWorkspaceKey() {
        return workspaceKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Channel getSourceChannel() {
        return sourceChannel;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<KanbanColumn> getColumns() {
        return columns;
    }

    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
