package com.ams.hrms.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ams.hrms.model.AuditLog;

/**
 * Access to {@code audit_logs}. Writes are append-only: only {@link #insert}
 * exists - no update or delete method may ever be added (spec section 28).
 * The read side powers the Audit Log viewer with server-side pagination.
 */
public class AuditRepository {

    private static final String SELECT =
            "SELECT a.id, a.user_id, COALESCE(u.username, '(system)') AS username, "
                    + "a.action, a.module, a.entity, a.entity_id, a.description, "
                    + "a.ip_address, a.device_info, a.created_at "
                    + "FROM audit_logs a LEFT JOIN users u ON u.id = a.user_id";

    // ------------------------------------------------------------------
    // Append-only write access
    // ------------------------------------------------------------------

    private static final String INSERT =
            "INSERT INTO audit_logs (user_id, action, module, entity, entity_id, description, ip_address, device_info) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    public void insert(AuditLog entry) {
        new Sql().executeInsert(INSERT,
                entry.userId(),
                entry.action(),
                entry.module(),
                entry.entity(),
                entry.entityId(),
                entry.description(),
                entry.ipAddress(),
                entry.deviceInfo());
    }

    // ------------------------------------------------------------------
    // Read access
    // ------------------------------------------------------------------

    public List<AuditRow> find(Filter filter, int offset, int limit) {
        SqlFragment where = buildWhere(filter);
        String sql = SELECT + " WHERE " + where.sql()
                + " ORDER BY a.id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(where.params());
        params.add(limit);
        params.add(Math.max(0, offset));
        return new Sql().list(sql, this::mapRow, params.toArray());
    }

    public long countMatching(Filter filter) {
        SqlFragment where = buildWhere(filter);
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM audit_logs a LEFT JOIN users u ON u.id = a.user_id "
                        + "WHERE " + where.sql(),
                where.params().toArray());
    }

    public List<String> distinctModules() {
        return new Sql().list(
                "SELECT DISTINCT module FROM audit_logs ORDER BY module",
                rs -> rs.getString(1));
    }

    public List<String> distinctActions() {
        return new Sql().list(
                "SELECT DISTINCT action FROM audit_logs ORDER BY action",
                rs -> rs.getString(1));
    }

    /** Users that appear in the trail; used for the user filter combo. */
    public List<UserOption> distinctUsers() {
        return new Sql().list(
                "SELECT DISTINCT u.id, u.username FROM audit_logs a "
                        + "JOIN users u ON u.id = a.user_id ORDER BY u.username",
                rs -> new UserOption(rs.getLong(1), rs.getString(2)));
    }

    // ------------------------------------------------------------------
    // Filter
    // ------------------------------------------------------------------

    /**
     * Viewer filter (spec section 28). Normalizes blanks in the compact
     * constructor so repositories always receive clean values.
     */
    public record Filter(String keyword, String action, String module, Long userId,
                         LocalDate fromDate, LocalDate toDate) {

        public Filter {
            keyword = keyword == null ? "" : keyword.trim();
            action = action == null || action.isBlank() ? "" : action.trim();
            module = module == null || module.isBlank() ? "" : module.trim();
        }

        public static Filter empty() {
            return new Filter("", "", "", null, null, null);
        }
    }

    /** Pure WHERE-clause builder - unit-tested without any database. */
    static SqlFragment buildWhere(Filter filter) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (!filter.keyword().isEmpty()) {
            conditions.add("(a.description LIKE ? OR a.entity LIKE ? "
                    + "OR a.action LIKE ? OR u.username LIKE ?)");
            String pattern = "%" + filter.keyword() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (!filter.action().isEmpty()) {
            conditions.add("a.action = ?");
            params.add(filter.action());
        }
        if (!filter.module().isEmpty()) {
            conditions.add("a.module = ?");
            params.add(filter.module());
        }
        if (filter.userId() != null) {
            conditions.add("a.user_id = ?");
            params.add(filter.userId());
        }
        if (filter.fromDate() != null) {
            conditions.add("a.created_at >= ?");
            params.add(filter.fromDate().atStartOfDay());
        }
        if (filter.toDate() != null) {
            conditions.add("a.created_at < ?");
            params.add(filter.toDate().plusDays(1).atStartOfDay());
        }

        String sql = conditions.isEmpty()
                ? "1 = 1"
                : String.join(" AND ", conditions);
        return new SqlFragment(sql, List.copyOf(params));
    }

    /** Immutable SQL fragment: condition text plus its bound parameters. */
    record SqlFragment(String sql, List<Object> params) {
    }

    public record UserOption(long id, String username) {
    }

    public record AuditRow(long id, Long userId, String username, String action,
                           String module, String entity, Long entityId, String description,
                           LocalDateTime createdAt, String ipAddress, String deviceInfo) {
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private AuditRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        long userId = rs.getLong("user_id");
        boolean userIdNull = rs.wasNull();
        long entityId = rs.getLong("entity_id");
        boolean entityIdNull = rs.wasNull();
        return new AuditRow(
                rs.getLong("id"),
                userIdNull ? null : userId,
                rs.getString("username"),
                rs.getString("action"),
                rs.getString("module"),
                rs.getString("entity"),
                entityIdNull ? null : entityId,
                rs.getString("description"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getString("ip_address"),
                rs.getString("device_info"));
    }
}
