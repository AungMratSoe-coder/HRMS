package com.ams.hrms.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.Role;
import com.ams.hrms.model.UserAccount;

/**
 * User persistence: authentication queries plus the administrative
 * account lifecycle (list, create, password reset, activation, role
 * assignment). Password hashes are only ever written through
 * {@link #updatePassword}, never logged.
 */
public class UserRepository {

        private static final String SELECT_ACCOUNT = "SELECT id, username, password_hash, full_name, email, phone, is_active, "
                        + "must_change_password, employee_id FROM users WHERE email = ?";

        private static final String SELECT_ACCOUNT_BY_ID = "SELECT id, username, password_hash, full_name, email, phone, is_active, "
                        + "must_change_password, employee_id FROM users WHERE id = ?";

        /**
         * List row for administration screens: account data without the hash,
         * plus a comma-separated role-name summary.
         */
        public record UserRow(long id, String username, String fullName, String email,
                        boolean active, boolean mustChangePassword,
                        LocalDateTime lastLoginAt, String roles) {
        }

        /** Assignable role reference (id needed for user_roles writes). */
        public record RoleRef(long id, String code, String name) {
        }

        /** Returns the account for an email address, including the password hash. */
        public Optional<UserAccount> findAccountByEmail(String email) {
                try (Sql sql = new Sql()) {
                        return sql.first(SELECT_ACCOUNT, this::mapAccount, email);
                }
        }

        /** Returns the account for an id, including the password hash. */
        public Optional<UserAccount> findAccountById(long userId) {
                try (Sql sql = new Sql()) {
                        return sql.first(SELECT_ACCOUNT_BY_ID, this::mapAccount, userId);
                }
        }

        private UserAccount mapAccount(java.sql.ResultSet rs) throws java.sql.SQLException {
                Object linkedEmployee = rs.getObject("employee_id");
                return new UserAccount(
                                rs.getLong("id"),
                                rs.getString("username"),
                                rs.getString("password_hash"),
                                rs.getString("full_name"),
                                rs.getString("email"),
                                rs.getString("phone"),
                                rs.getBoolean("is_active"),
                                rs.getBoolean("must_change_password"),
                                linkedEmployee == null ? null : rs.getLong("employee_id"));
        }

        /** All accounts for administration screens, newest first. */
        public List<UserRow> findAll() {
                try (Sql sql = new Sql()) {
                        return sql.list(
                                        "SELECT u.id, u.username, u.full_name, u.email, u.is_active, "
                                                        + "u.must_change_password, u.last_login_at, "
                                                        + "COALESCE((SELECT GROUP_CONCAT(r.role_name ORDER BY r.role_name SEPARATOR ', ') "
                                                        + "FROM roles r JOIN user_roles ur ON ur.role_id = r.id "
                                                        + "WHERE ur.user_id = u.id), '-') AS roles "
                                                        + "FROM users u ORDER BY u.id",
                                        this::mapRow);
                }
        }

        /** One administration row by id. */
        public Optional<UserRow> findRowById(long userId) {
                try (Sql sql = new Sql()) {
                        return sql.first(
                                        "SELECT u.id, u.username, u.full_name, u.email, u.is_active, "
                                                        + "u.must_change_password, u.last_login_at, "
                                                        + "COALESCE((SELECT GROUP_CONCAT(r.role_name ORDER BY r.role_name SEPARATOR ', ') "
                                                        + "FROM roles r JOIN user_roles ur ON ur.role_id = r.id "
                                                        + "WHERE ur.user_id = u.id), '-') AS roles "
                                                        + "FROM users u WHERE u.id = ?",
                                        this::mapRow, userId);
                }
        }

        private UserRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
                return new UserRow(
                                rs.getLong("id"),
                                rs.getString("username"),
                                rs.getString("full_name"),
                                rs.getString("email"),
                                rs.getBoolean("is_active"),
                                rs.getBoolean("must_change_password"),
                                rs.getObject("last_login_at", LocalDateTime.class),
                                rs.getString("roles"));
        }

        /** Creates an account; returns the new id. Caller hashes the password. */
        public long insert(String username, String passwordHash, String fullName, String email) {
                try (Sql sql = new Sql()) {
                        return sql.executeInsert(
                                        "INSERT INTO users (username, password_hash, full_name, email) "
                                                        + "VALUES (?, ?, ?, ?)",
                                        username, passwordHash, fullName, email);
                }
        }

        /** Writes a new password hash. */
        public void updatePassword(long userId, String passwordHash) {
                try (Sql sql = new Sql()) {
                        sql.executeUpdate(
                                        "UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, userId);
                }
        }

        /** Updates the self-maintained contact fields of an account. */
        public void updateProfile(long userId, String email, String phone) {
                try (Sql sql = new Sql()) {
                        sql.executeUpdate(
                                        "UPDATE users SET email = ?, phone = ? WHERE id = ?", email, phone, userId);
                }
        }

        /**
         * Stored profile picture of a user (preprocessed square JPEG thumbnail),
         * or null when none was uploaded. Deliberately not part of the auth
         * selects so sign-in queries stay light.
         */
        public byte[] findAvatar(long userId) {
                try (Sql sql = new Sql()) {
                        List<byte[]> found = sql.list("SELECT avatar FROM users WHERE id = ?",
                                        rs -> rs.getBytes("avatar"), userId);
                        return found.isEmpty() ? null : found.get(0);
                }
        }

        /** Writes the profile picture; pass null to remove it. */
        public void updateAvatar(long userId, byte[] avatarJpeg) {
                try (Sql sql = new Sql()) {
                        sql.executeUpdate(
                                        "UPDATE users SET avatar = ? WHERE id = ?", avatarJpeg, userId);
                }
        }

        /** True when another account already uses the given username. */
        public boolean usernameExists(String username) {
                try (Sql sql = new Sql()) {
                        return sql.first("SELECT id FROM users WHERE username = ?",
                                        rs -> rs.getLong("id"), username).isPresent();
                }
        }

        /** True when another account already uses the given email. */
        public boolean emailTakenByOther(String email, long excludeUserId) {
                if (email == null || email.isBlank()) {
                        return false;
                }
                try (Sql sql = new Sql()) {
                        return sql.first(
                                        "SELECT id FROM users WHERE email = ? AND id <> ?",
                                        rs -> rs.getLong("id"), email, excludeUserId).isPresent();
                }
        }

        /**
         * Links the account to the employee sharing its email, unless a link
         * already exists (mirrors the V5 migration backfill for new accounts).
         */
        public void linkByEmailIfUnlinked(long userId) {
                try (Sql sql = new Sql()) {
                        sql.executeUpdate(
                                        "UPDATE users u SET u.employee_id = ("
                                                        + "SELECT MIN(e.id) FROM employees e "
                                                        + "WHERE e.email IS NOT NULL AND LOWER(e.email) = LOWER(u.email)) "
                                                        + "WHERE u.id = ? AND u.employee_id IS NULL",
                                        userId);
                }
        }

        /** Sets or clears (null) the employee record owned by this account. */
        public void setEmployeeLink(long userId, Long employeeId) {
                try (Sql sql = new Sql()) {
                        sql.executeUpdate(
                                        "UPDATE users SET employee_id = ? WHERE id = ?", employeeId, userId);
                }
        }

        /** Account currently linked to the given employee, if any. */
        public Long findUserIdByEmployeeId(long employeeId) {
                try (Sql sql = new Sql()) {
                        return sql.first("SELECT id FROM users WHERE employee_id = ?",
                                        rs -> rs.getLong("id"), employeeId).orElse(null);
                }
        }

        public void setMustChangePassword(long userId, boolean mustChange) {
                try (Sql sql = new Sql()) {
                        sql.executeUpdate(
                                        "UPDATE users SET must_change_password = ? WHERE id = ?", mustChange, userId);
                }
        }

        public void setActive(long userId, boolean active) {
                try (Sql sql = new Sql()) {
                        sql.executeUpdate(
                                        "UPDATE users SET is_active = ? WHERE id = ?", active, userId);
                }
        }

        /** Role ids currently assigned to a user. */
        public List<Long> findRoleIds(long userId) {
                try (Sql sql = new Sql()) {
                        return sql.list("SELECT role_id FROM user_roles WHERE user_id = ?",
                                        rs -> rs.getLong("role_id"), userId);
                }
        }

        /** Replaces the role assignment of a user atomically. */
        public void replaceRoles(long userId, List<Long> roleIds) {
                TransactionManager.execute(sql -> {
                        sql.executeUpdate("DELETE FROM user_roles WHERE user_id = ?", userId);
                        if (roleIds != null) {
                                for (Long roleId : roleIds) {
                                        sql.executeUpdate(
                                                        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                                                        userId, roleId);
                                }
                        }
                        return null;
                });
        }

        /** All assignable roles for pickers, ordered by code. */
        public List<RoleRef> findAllRoles() {
                try (Sql sql = new Sql()) {
                        return sql.list(
                                        "SELECT id, role_code, role_name FROM roles ORDER BY role_code",
                                        rs -> new RoleRef(rs.getLong("id"),
                                                        rs.getString("role_code"), rs.getString("role_name")));
                }
        }

        public void touchLastLogin(long userId) {
                try (Sql sql = new Sql()) {
                        sql.executeUpdate("UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
                }
        }

        public List<Role> findRoles(long userId) {
                try (Sql sql = new Sql()) {
                        return sql.list(
                                        "SELECT r.role_code, r.role_name, r.description "
                                                        + "FROM roles r JOIN user_roles ur ON ur.role_id = r.id "
                                                        + "WHERE ur.user_id = ? ORDER BY r.role_code",
                                        resultSet -> new Role(
                                                        resultSet.getString("role_code"),
                                                        resultSet.getString("role_name"),
                                                        resultSet.getString("description")),
                                        userId);
                }
        }

        public List<String> findPermissionCodes(long userId) {
                try (Sql sql = new Sql()) {
                        return sql.list(
                                        "SELECT DISTINCT p.perm_code "
                                                        + "FROM permissions p "
                                                        + "JOIN role_permissions rp ON rp.permission_id = p.id "
                                                        + "JOIN user_roles ur ON ur.role_id = rp.role_id "
                                                        + "WHERE ur.user_id = ?",
                                        resultSet -> resultSet.getString("perm_code"),
                                        userId);
                }
        }
}
