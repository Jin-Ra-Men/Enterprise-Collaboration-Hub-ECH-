package com.ech.backend.domain.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "release_versions")
public class ReleaseVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String version;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    /** SHA-256 hex (64자) */
    @Column(name = "checksum", length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReleaseStatus status = ReleaseStatus.UPLOADED;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt = OffsetDateTime.now();

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    protected ReleaseVersion() {
    }

    public ReleaseVersion(String version, String fileName, String filePath,
                          long fileSize, String checksum, String description, Long uploadedBy) {
        this.version = version;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.description = description;
        this.uploadedBy = uploadedBy;
    }

    public Long getId() { return id; }
    public String getVersion() { return version; }
    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public long getFileSize() { return fileSize; }
    public String getChecksum() { return checksum; }
    public ReleaseStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public Long getUploadedBy() { return uploadedBy; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }

    public void activate() {
        this.status = ReleaseStatus.ACTIVE;
        this.activatedAt = OffsetDateTime.now();
    }

    public void markPrevious() {
        this.status = ReleaseStatus.PREVIOUS;
    }

    public void deprecate() {
        this.status = ReleaseStatus.DEPRECATED;
    }
}
