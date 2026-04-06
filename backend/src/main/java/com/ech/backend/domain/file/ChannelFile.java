package com.ech.backend.domain.file;

import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "channel_files")
public class ChannelFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", referencedColumnName = "employee_no", nullable = false)
    private User uploadedBy;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 1024)
    private String storageKey;

    /** 미리보기·압축본(썸네일용). 없으면 null — 기존 행 호환 */
    @Column(name = "preview_storage_key", length = 1024)
    private String previewStorageKey;

    @Column(name = "preview_size_bytes")
    private Long previewSizeBytes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected ChannelFile() {
    }

    public ChannelFile(
            Channel channel,
            User uploadedBy,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String storageKey,
            String previewStorageKey,
            Long previewSizeBytes
    ) {
        this.channel = channel;
        this.uploadedBy = uploadedBy;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.previewStorageKey = previewStorageKey;
        this.previewSizeBytes = previewSizeBytes;
    }

    public Long getId() {
        return id;
    }

    public Channel getChannel() {
        return channel;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getPreviewStorageKey() {
        return previewStorageKey;
    }

    public Long getPreviewSizeBytes() {
        return previewSizeBytes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
