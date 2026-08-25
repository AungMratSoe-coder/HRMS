package com.ams.hrms.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.ams.hrms.exception.ConfigurationException;
import com.ams.hrms.exception.DataAccessException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the HikariCP connection pool. Initialized exactly once during startup;
 * repositories obtain connections exclusively through this class so pooling,
 * timeouts and leak detection are applied uniformly.
 */
public final class DatabaseConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConfig.class);

    private static final long MAX_LIFETIME_MINUTES = 30;
    private static final long IDLE_TIMEOUT_MINUTES = 10;
    private static final long LEAK_DETECTION_SECONDS = 60;

    private static volatile HikariDataSource dataSource;

    private DatabaseConfig() {
    }

    /** Builds the connection pool from the given configuration. Idempotent. */
    public static synchronized void initialize(AppConfig config) {
        if (dataSource != null) {
            return;
        }
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.dbUrl());
        hikari.setUsername(config.dbUsername());
        hikari.setPassword(config.dbPassword());
        hikari.setPoolName("HRMS-Pool");
        hikari.setMaximumPoolSize(config.poolMaxSize());
        hikari.setMinimumIdle(config.poolMinIdle());
        hikari.setConnectionTimeout(TimeUnit.SECONDS.toMillis(config.poolConnectionTimeoutSeconds()));
        hikari.setMaxLifetime(TimeUnit.MINUTES.toMillis(MAX_LIFETIME_MINUTES));
        hikari.setIdleTimeout(TimeUnit.MINUTES.toMillis(IDLE_TIMEOUT_MINUTES));
        hikari.setLeakDetectionThreshold(TimeUnit.SECONDS.toMillis(LEAK_DETECTION_SECONDS));

        try {
            dataSource = new HikariDataSource(hikari);
        } catch (RuntimeException e) {
            throw new ConfigurationException("Failed to initialize the database connection pool: " + e.getMessage(), e);
        }
        LOG.info("Connection pool initialized (max={}, minIdle={}, timeout={}s)",
                config.poolMaxSize(), config.poolMinIdle(), config.poolConnectionTimeoutSeconds());
    }

    /**
     * Returns a pooled connection. Inside {@link com.ams.hrms.repository.TransactionManager}
     * transactions the transaction's shared connection is used instead, so
     * callers may always just ask for a connection.
     */
    public static Connection getConnection() {
        HikariDataSource current = dataSource;
        if (current == null || current.isClosed()) {
            throw new IllegalStateException("Connection pool has not been initialized");
        }
        try {
            return current.getConnection();
        } catch (SQLException e) {
            throw new DataAccessException("Could not obtain a database connection from the pool", e);
        }
    }

    /** Read-only access for libraries that need a DataSource (e.g. reporting). */
    public static DataSource dataSource() {
        return dataSource;
    }

    /** Gracefully shuts the pool down; registered as a JVM shutdown hook. */
    public static synchronized void close() {
        HikariDataSource current = dataSource;
        if (current != null && !current.isClosed()) {
            current.close();
            LOG.info("Connection pool closed");
        }
        dataSource = null;
    }
}
