package com.ams.hrms.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.ams.hrms.model.Notification;

/**
 * Persistence for the {@code notifications} table (spec section 41) and the
 * supporting lookups used by the operational scan. All statements go through
 * {@link Sql} (PreparedStatement only).
 */
public class NotificationRepository {

    private static final String SELECT =
            "SELECT id, user_id, title, message, notification_type, reference_module, "
                    + "reference_id, is_read, read_at, created_at FROM notifications";

    // ------------------------------------------------------------------
    // Write access
    // ------------------------------------------------------------------

    public long insert(Notification notification) {
        return new Sql().executeInsert(
                "INSERT INTO notifications (user_id, title, message, notification_type, "
                        + "reference_module, reference_id) VALUES (?, ?, ?, ?, ?, ?)",
                notification.getUserId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.getReferenceModule(),
                notification.getReferenceId());
    }

    /** Marks one row read; returns true when the row was still unread. */
    public boolean markRead(long id, long userId) {
        return new Sql().executeUpdate(
                "UPDATE notifications SET is_read = 1, read_at = NOW() "
                        + "WHERE id = ? AND (user_id = ? OR user_id IS NULL) AND is_read = 0",
                id, userId) == 1;
    }

    public int markAllRead(long userId) {
        return new Sql().executeUpdate(
                "UPDATE notifications SET is_read = 1, read_at = NOW() "
                        + "WHERE (user_id = ? OR user_id IS NULL) AND is_read = 0",
                userId);
    }

    /** Housekeeping: removes read notifications older than {@code days}. */
    public int purgeReadOlderThanDays(int days) {
        return new Sql().executeUpdate(
                "DELETE FROM notifications WHERE is_read = 1 AND read_at IS NOT NULL "
                        + "AND read_at < NOW() - INTERVAL ? DAY",
                days);
    }

    // ------------------------------------------------------------------
    // Read access
    // ------------------------------------------------------------------

    /** Newest-first feed for one user (personal rows + legacy broadcasts). */
    public List<Notification> findForUser(long userId, boolean onlyUnread, int limit) {
        StringBuilder sql = new StringBuilder(SELECT)
                .append(" WHERE (user_id = ? OR user_id IS NULL)");
        if (onlyUnread) {
            sql.append(" AND is_read = 0");
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        return new Sql().list(sql.toString(), this::mapRow, userId, limit);
    }

    public long countUnread(long userId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM notifications "
                        + "WHERE (user_id = ? OR user_id IS NULL) AND is_read = 0",
                userId);
    }

    /**
     * Dedup guard for the pending-leave digest: true when the user already
     * has an unread copy of this logical item (null-safe on module/id).
     * Deliberately unread-only so the digest re-raises on each scan while
     * requests are still pending.
     */
    public boolean existsUnread(Long userId, String type, String refModule, Long refId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM notifications WHERE user_id <=> ? "
                        + "AND notification_type = ? AND reference_module <=> ? "
                        + "AND reference_id <=> ? AND is_read = 0",
                userId, type, refModule, refId) > 0;
    }

    /**
     * Dedup guard for one-shot scan notices (document expiry, birthdays,
     * training reminders): true when the user already has a copy of this
     * logical item - read OR unread - created on/after {@code minCreatedAt}
     * (null = any time). Matching read rows too is what keeps a dismissed
     * warning from being silently recreated on the next app start.
     */
    public boolean existsSimilar(Long userId, String type, String refModule, Long refId,
                                 LocalDateTime minCreatedAt) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM notifications WHERE user_id <=> ? "
                        + "AND notification_type = ? AND reference_module <=> ? "
                        + "AND reference_id <=> ? AND created_at >= ?",
                userId, type, refModule, refId,
                minCreatedAt == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : minCreatedAt) > 0;
    }

    public List<Long> findActiveUserIds() {
        return new Sql().list(
                "SELECT id FROM users WHERE is_active = 1 ORDER BY id",
                rs -> rs.getLong(1));
    }

    public List<Long> findUserIdsWithPermission(String permCode) {
        return new Sql().list(
                "SELECT DISTINCT ur.user_id FROM user_roles ur "
                        + "JOIN role_permissions rp ON rp.role_id = ur.role_id "
                        + "JOIN permissions p ON p.id = rp.permission_id "
                        + "WHERE p.perm_code = ? ORDER BY ur.user_id",
                rs -> rs.getLong(1),
                permCode);
    }

    /** Primary active user account sharing an employee's email, or null. */
    public Long findUserIdLinkedToEmployee(long employeeId) {
        return new Sql().first(
                "SELECT u.id FROM users u "
                        + "WHERE u.email = (SELECT email FROM employees WHERE id = ?) "
                        + "AND u.is_active = 1 ORDER BY u.id LIMIT 1",
                rs -> rs.getLong(1), employeeId).orElse(null);
    }

    public int intSetting(String settingKey, int fallbackValue) {
        return new Sql().first(
                "SELECT setting_value FROM app_settings WHERE setting_key = ?",
                rs -> rs.getString(1), settingKey)
                .map(raw -> {
                    try {
                        return Integer.parseInt(raw.trim());
                    } catch (NumberFormatException e) {
                        return fallbackValue;
                    }
                })
                .orElse(fallbackValue);
    }

    public long countPendingLeaveRequests() {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM leave_requests WHERE status = 'PENDING'");
    }

    // ------------------------------------------------------------------
    // Operational scan sources
    // ------------------------------------------------------------------

    public List<ExpiringDocument> findExpiringDocuments(int warningDays) {
        return new Sql().list(
                "SELECT d.id, d.document_type, d.file_name, e.full_name, d.expiry_date "
                        + "FROM employee_documents d "
                        + "JOIN employees e ON e.id = d.employee_id "
                        + "WHERE d.status = 'ACTIVE' AND d.expiry_date IS NOT NULL "
                        + "AND d.expiry_date BETWEEN CURDATE() AND CURDATE() + INTERVAL ? DAY "
                        + "ORDER BY d.expiry_date",
                this::mapExpiring,
                warningDays);
    }

    public List<BirthdayEmployee> findBirthdays(LocalDate today) {
        return new Sql().list(
                "SELECT e.id, e.full_name, COALESCE(d.dept_name, '-') AS dept_name "
                        + "FROM employees e LEFT JOIN departments d ON d.id = e.department_id "
                        + "WHERE e.status = 'ACTIVE' "
                        + "AND MONTH(e.date_of_birth) = ? AND DAY(e.date_of_birth) = ? "
                        + "ORDER BY e.full_name",
                this::mapBirthday,
                today.getMonthValue(),
                today.getDayOfMonth());
    }

    public List<UpcomingSession> findUpcomingSessions(int reminderDays) {
        return new Sql().list(
                "SELECT s.id, p.program_name, s.start_datetime, s.location "
                        + "FROM training_sessions s "
                        + "JOIN training_programs p ON p.id = s.training_program_id "
                        + "WHERE s.status = 'SCHEDULED' "
                        + "AND s.start_datetime BETWEEN NOW() AND NOW() + INTERVAL ? DAY "
                        + "ORDER BY s.start_datetime",
                this::mapSession,
                reminderDays);
    }

    /** Active user accounts of employees enrolled in a session. */
    public List<Long> findEnrolledUserIds(long sessionId) {
        return new Sql().list(
                "SELECT DISTINCT u.id FROM employee_trainings et "
                        + "JOIN employees e ON e.id = et.employee_id "
                        + "JOIN users u ON u.email = e.email AND u.is_active = 1 "
                        + "WHERE et.session_id = ? ORDER BY u.id",
                rs -> rs.getLong(1),
                sessionId);
    }

    // ------------------------------------------------------------------
    // Scan result records
    // ------------------------------------------------------------------

    public record ExpiringDocument(long documentId, String documentType, String fileName,
                                   String employeeName, LocalDate expiryDate) {
    }

    public record BirthdayEmployee(long employeeId, String fullName, String departmentName) {
    }

    public record UpcomingSession(long sessionId, String programName,
                                  LocalDateTime startDateTime, String location) {
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private Notification mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Notification notification = new Notification();
        notification.setId(rs.getLong("id"));
        long userId = rs.getLong("user_id");
        notification.setUserId(rs.wasNull() ? null : userId);
        notification.setTitle(rs.getString("title"));
        notification.setMessage(rs.getString("message"));
        notification.setType(rs.getString("notification_type"));
        notification.setReferenceModule(rs.getString("reference_module"));
        long referenceId = rs.getLong("reference_id");
        notification.setReferenceId(rs.wasNull() ? null : referenceId);
        notification.setRead(rs.getBoolean("is_read"));
        notification.setReadAt(rs.getObject("read_at", LocalDateTime.class));
        notification.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return notification;
    }

    private ExpiringDocument mapExpiring(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExpiringDocument(
                rs.getLong("id"),
                rs.getString("document_type"),
                rs.getString("file_name"),
                rs.getString("full_name"),
                rs.getObject("expiry_date", LocalDate.class));
    }

    private BirthdayEmployee mapBirthday(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BirthdayEmployee(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("dept_name"));
    }

    private UpcomingSession mapSession(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UpcomingSession(
                rs.getLong("id"),
                rs.getString("program_name"),
                rs.getObject("start_datetime", LocalDateTime.class),
                rs.getString("location"));
    }
}
