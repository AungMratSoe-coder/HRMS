package com.ams.hrms.model;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * One notification row (spec section 41). Rows are always created for a
 * concrete recipient so read-state is per user; {@code userId} remains
 * nullable in the schema for legacy broadcast rows.
 */
public class Notification {

    public static final String TYPE_INFO = "INFO";
    public static final String TYPE_WARNING = "WARNING";
    public static final String TYPE_SUCCESS = "SUCCESS";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_LEAVE = "LEAVE";
    public static final String TYPE_PAYROLL = "PAYROLL";
    public static final String TYPE_DOCUMENT = "DOCUMENT";
    public static final String TYPE_TRAINING = "TRAINING";
    public static final String TYPE_SYSTEM = "SYSTEM";

    /** Values allowed by the chk_notif_type constraint. */
    public static final Set<String> TYPES = Set.of(
            TYPE_INFO, TYPE_WARNING, TYPE_SUCCESS, TYPE_ERROR, TYPE_LEAVE,
            TYPE_PAYROLL, TYPE_DOCUMENT, TYPE_TRAINING, TYPE_SYSTEM);

    private Long id;
    private Long userId;
    private String title;
    private String message;
    private String type;
    private String referenceModule;
    private Long referenceId;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public boolean isUnread() {
        return !read;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReferenceModule() {
        return referenceModule;
    }

    public void setReferenceModule(String referenceModule) {
        this.referenceModule = referenceModule;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
