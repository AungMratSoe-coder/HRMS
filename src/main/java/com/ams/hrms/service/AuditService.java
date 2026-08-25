package com.ams.hrms.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.AuditLog;
import com.ams.hrms.repository.AuditRepository;
import com.ams.hrms.repository.AuditRepository.Filter;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.security.SessionContext;

/**
 * Audit trail service (spec section 28). Writing is a system-side concern:
 * every service reports here and failures never break the business
 * operation. Reading is privileged: queries require {@code AUDIT_LOG_VIEW}
 * at the service layer, never relying on UI hiding alone.
 */
public class AuditService {

    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String ACTION_PASSWORD_RESET = "PASSWORD_RESET";

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;
    private volatile String cachedHostAddress;
    private volatile String cachedHostName;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Records one audit entry. The user is taken from the current session
     * (may be null, e.g. failed login before authentication).
     */
    public void record(String action, String module, String entity, Long entityId, String description) {
        AuditLog entry = new AuditLog(
                SessionContext.currentUserId(),
                action,
                module,
                entity,
                entityId,
                description,
                resolveHostAddress(),
                resolveDeviceInfo());
        try {
            auditRepository.insert(entry);
        } catch (RuntimeException e) {
            LOG.error("Failed to write audit entry [{} / {}]: {}", action, module, e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Read access (AUDIT_LOG_VIEW required)
    // ------------------------------------------------------------------

    /** One server-side page of the trail, newest first. */
    public List<AuditRepository.AuditRow> search(Filter filter, int offset, int limit) {
        SecurityService.require(Permissions.AUDIT_LOG_VIEW);
        validateRange(filter);
        return auditRepository.find(filter, offset, limit);
    }

    public long countMatching(Filter filter) {
        SecurityService.require(Permissions.AUDIT_LOG_VIEW);
        validateRange(filter);
        return auditRepository.countMatching(filter);
    }

    public List<String> distinctModules() {
        SecurityService.require(Permissions.AUDIT_LOG_VIEW);
        return auditRepository.distinctModules();
    }

    public List<String> distinctActions() {
        SecurityService.require(Permissions.AUDIT_LOG_VIEW);
        return auditRepository.distinctActions();
    }

    public List<AuditRepository.UserOption> distinctUsers() {
        SecurityService.require(Permissions.AUDIT_LOG_VIEW);
        return auditRepository.distinctUsers();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Friendly date-range rule; the WHERE builder treats days inclusively. */
    private void validateRange(Filter filter) {
        LocalDate from = filter.fromDate();
        LocalDate to = filter.toDate();
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException(List.of(
                    "The from date cannot be after the to date."));
        }
    }

    /** "username @ hostname" - best effort, never throws. */
    private String resolveDeviceInfo() {
        String os = System.getProperty("os.name", "unknown");
        String user = System.getProperty("user.name", "unknown");
        return user + " @ " + resolveHostname() + " (" + os + ")";
    }

    private synchronized String resolveHostname() {
        if (cachedHostName == null) {
            try {
                InetAddress local = InetAddress.getLocalHost();
                cachedHostName = local.getHostName();
                cachedHostAddress = local.getHostAddress();
            } catch (UnknownHostException e) {
                cachedHostName = "unknown-host";
                cachedHostAddress = "unknown-ip";
            }
        }
        return cachedHostName;
    }

    private synchronized String resolveHostAddress() {
        if (cachedHostAddress == null) {
            resolveHostname();
        }
        return cachedHostAddress;
    }
}
