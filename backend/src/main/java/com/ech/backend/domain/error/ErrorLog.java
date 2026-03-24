package com.ech.backend.domain.error;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "error_logs")
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "error_code", nullable = false, length = 50)
    private String errorCode;

    @Column(name = "error_class", nullable = false, length = 255)
    private String errorClass;

    @Column(name = "message", length = 2000)
    private String message;

    @Column(name = "path", length = 500)
    private String path;

    @Column(name = "http_method", length = 20)
    private String httpMethod;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected ErrorLog() {
    }

    public ErrorLog(
            String errorCode,
            String errorClass,
            String message,
            String path,
            String httpMethod,
            Long actorUserId,
            String requestId
    ) {
        this.errorCode = errorCode;
        this.errorClass = errorClass;
        this.message = message;
        this.path = path;
        this.httpMethod = httpMethod;
        this.actorUserId = actorUserId;
        this.requestId = requestId;
    }

    public Long getId() {
        return id;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorClass() {
        return errorClass;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getRequestId() {
        return requestId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
