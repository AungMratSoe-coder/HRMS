package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.exception.DataAccessException;

/**
 * Small, safe JDBC facade used by every repository.
 *
 * <ul>
 *   <li>Always uses {@link PreparedStatement} - user input is never
 *       concatenated into SQL.</li>
 *   <li>Parameters bind {@link LocalDate}/{@link LocalTime}/
 *       {@link LocalDateTime}, {@link BigDecimal}, enums (stored as names)
 *       and nulls correctly.</li>
 *   <li>Instances created with {@code new Sql()} borrow a pooled connection
 *       per statement and release it immediately. Inside a transaction,
 *       {@link TransactionManager} supplies an instance bound to the
 *       transaction's shared connection.</li>
 *   <li>All checked JDBC exceptions are translated into
 *       {@link DataAccessException}; the failing SQL is summarized for the log
 *       and never shown to users.</li>
 * </ul>
 *
 * <p>API summary:</p>
 * <ul>
 *   <li>static facade for standalone statements: {@code Sql.query / queryOne /
 *       update / insert / count}</li>
 *   <li>instance API for transaction-bound work: {@code list / first /
 *       scalarLong / executeUpdate / executeInsert}</li>
 * </ul>
 */
public final class Sql implements AutoCloseable {

    private final Connection dedicated;

    /** Creates a standalone Sql that borrows a pooled connection per statement. */
    public Sql() {
        this.dedicated = null;
    }

    /** Package-private: binds this Sql to an open transaction connection. */
    Sql(Connection transactionConnection) {
        this.dedicated = transactionConnection;
    }

    // ------------------------------------------------------------------
    // Static facade for non-transactional access
    // ------------------------------------------------------------------

    public static <T> List<T> query(String sqlStatement, RowMapper<T> mapper, Object... params) {
        try (Sql sql = new Sql()) {
            return sql.list(sqlStatement, mapper, params);
        }
    }

    public static <T> Optional<T> queryOne(String sqlStatement, RowMapper<T> mapper, Object... params) {
        try (Sql sql = new Sql()) {
            return sql.first(sqlStatement, mapper, params);
        }
    }

    public static int update(String sqlStatement, Object... params) {
        try (Sql sql = new Sql()) {
            return sql.executeUpdate(sqlStatement, params);
        }
    }

    public static long insert(String sqlStatement, Object... params) {
        try (Sql sql = new Sql()) {
            return sql.executeInsert(sqlStatement, params);
        }
    }

    public static long count(String sqlStatement, Object... params) {
        return queryOne(sqlStatement, resultSet -> resultSet.getLong(1), params).orElse(0L);
    }

    // ------------------------------------------------------------------
    // Instance API (transaction-aware)
    // ------------------------------------------------------------------

    public <T> List<T> list(String sqlStatement, RowMapper<T> mapper, Object... params) {
        Connection connection = open();
        try (PreparedStatement statement = connection.prepareStatement(sqlStatement)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw translate("Query failed", sqlStatement, e);
        } finally {
            release(connection);
        }
    }

    /** Returns the first matching row mapped, or empty when none. */
    public <T> Optional<T> first(String sqlStatement, RowMapper<T> mapper, Object... params) {
        List<T> rows = list(sqlStatement, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Returns the first column of the first row as a long (0 when absent). */
    public long scalarLong(String sqlStatement, Object... params) {
        List<Long> rows = list(sqlStatement, rs -> rs.getLong(1), params);
        return rows.isEmpty() ? 0L : rows.get(0);
    }

    /** Executes an INSERT/UPDATE/DELETE and returns the affected row count. */
    public int executeUpdate(String sqlStatement, Object... params) {
        Connection connection = open();
        try (PreparedStatement statement = connection.prepareStatement(sqlStatement)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw translate("Update failed", sqlStatement, e);
        } finally {
            release(connection);
        }
    }

    /**
     * Executes an INSERT and returns the generated primary key, or -1 when the
     * driver reports no generated key.
     */
    public long executeInsert(String sqlStatement, Object... params) {
        Connection connection = open();
        try (PreparedStatement statement =
                     connection.prepareStatement(sqlStatement, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            throw translate("Insert failed", sqlStatement, e);
        } finally {
            release(connection);
        }
    }

    /** Closes the dedicated transaction connection if one is bound. */
    @Override
    public void close() {
        if (dedicated != null) {
            closeQuietly(dedicated);
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private Connection open() {
        return dedicated != null ? dedicated : DatabaseConfig.getConnection();
    }

    private void release(Connection connection) {
        if (connection != null && connection != dedicated) {
            closeQuietly(connection);
        }
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Pool reclaims physical connections; nothing useful to do here.
        }
    }

    private void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = normalize(params[i]);
            if (value == null) {
                statement.setNull(i + 1, Types.NULL);
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    private Object normalize(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value;
    }

    private DataAccessException translate(String prefix, String sqlStatement, SQLException cause) {
        return new DataAccessException(prefix + " [" + brief(sqlStatement) + "]: " + cause.getMessage(), cause);
    }

    private String brief(String sqlStatement) {
        String flattened = sqlStatement.replaceAll("\\s+", " ").trim();
        return flattened.length() <= 80 ? flattened : flattened.substring(0, 80) + "...";
    }
}
