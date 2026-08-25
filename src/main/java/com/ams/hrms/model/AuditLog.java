package com.ams.hrms.model;

/** One append-only audit trail entry. */
public record AuditLog(Long userId,
                       String action,
                       String module,
                       String entity,
                       Long entityId,
                       String description,
                       String ipAddress,
                       String deviceInfo) {
}
