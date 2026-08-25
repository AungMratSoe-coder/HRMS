package com.ams.hrms.repository;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.DataAccessException;

/**
 * Demarcates database transactions for multi-statement business operations
 * (payroll processing, hiring, leave approval, asset assignment).
 *
 * <p>Usage from services:</p>
 * <pre>{@code
 * long newId = TransactionManager.execute(tx ->
 *         employeeRepository.insert(tx, employee));
 * }</pre>
 *
 * <p>The work unit receives a {@link Sql} bound to the transaction's shared
 * connection. On any exception the whole unit rolls back; nested transactions
 * are rejected so commit boundaries stay explicit.</p>
 */
public final class TransactionManager {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionManager.class);

    private static final ThreadLocal<Connection> ACTIVE_TRANSACTION = new ThreadLocal<>();

    private TransactionManager() {
    }

    /** True while a transaction is open on the current thread. */
    public static boolean inTransaction() {
        return ACTIVE_TRANSACTION.get() != null;
    }

    /**
     * Runs the work inside a single transaction: commits on success, rolls
     * back on any failure.
     *
     * @param work unit of business logic receiving a transaction-bound {@link Sql}
     * @param <T>  result type produced by the work
     * @return the value returned by the work unit
     */
    public static <T> T execute(TransactionWork<T> work) {
        if (inTransaction()) {
            throw new BusinessException(
                    "Nested transactions are not supported",
                    "An internal error occurred. Please contact your administrator.");
        }

        Connection connection = DatabaseConfig.getConnection();
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            ACTIVE_TRANSACTION.set(connection);

            T result = work.run(new Sql(connection));

            connection.commit();
            return result;

        } catch (RuntimeException | Error e) {
            rollbackQuietly(connection);
            throw e;
        } catch (Exception e) {
            rollbackQuietly(connection);
            throw new DataAccessException("Transaction failed", e);
        } finally {
            ACTIVE_TRANSACTION.remove();
            restoreAutoCommitQuietly(connection, previousAutoCommit);
            closeQuietly(connection);
        }
    }

    /** Unit of work executed inside a transaction. */
    @FunctionalInterface
    public interface TransactionWork<T> {

        T run(Sql sql) throws Exception;
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            if (connection != null) {
                connection.rollback();
            }
        } catch (SQLException e) {
            LOG.warn("Transaction rollback failed", e);
        }
    }

    private static void restoreAutoCommitQuietly(Connection connection, boolean autoCommit) {
        try {
            if (connection != null) {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            LOG.debug("Could not restore autocommit on pooled connection", e);
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            LOG.debug("Could not close pooled connection", e);
        }
    }
}
