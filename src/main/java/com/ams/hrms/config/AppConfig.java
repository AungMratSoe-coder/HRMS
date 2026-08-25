package com.ams.hrms.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.ams.hrms.exception.ConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central application configuration. Reads {@code application.properties} from
 * the classpath and resolves values with the following precedence:
 * <ol>
 *   <li>dedicated environment variable (e.g. {@code HRMS_DB_PASSWORD})</li>
 *   <li>{@code ${ENV_VAR}} placeholder inside the property value</li>
 *   <li>the property value itself</li>
 *   <li>the caller-supplied default</li>
 * </ol>
 * No secret is ever logged by this class.
 */
public final class AppConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfig.class);

    private static AppConfig instance;

    private final Properties properties;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    /** Loads and caches the configuration. Idempotent. */
    public static synchronized AppConfig get() {
        if (instance == null) {
            Properties loaded = new Properties();
            try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
                if (in == null) {
                    throw new ConfigurationException("application.properties was not found on the classpath");
                }
                loaded.load(in);
            } catch (IOException e) {
                throw new ConfigurationException("Could not read application.properties", e);
            }
            instance = new AppConfig(loaded);
            LOG.info("Configuration loaded");
        }
        return instance;
    }

    // ------------------------------------------------------------------
    // Generic access
    // ------------------------------------------------------------------

    /** Plain property lookup with default; no environment resolution. */
    public String get(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    // ------------------------------------------------------------------
    // Application settings
    // ------------------------------------------------------------------

    public String appName() {
        return get("app.name", "HR Management System");
    }

    public String appVersion() {
        return get("app.version", "1.0.0");
    }

    /** Root directory for employee document files (file-system storage). */
    public String documentsRoot() {
        String fallback = System.getProperty("user.home") + File.separator + "HRMS" + File.separator + "documents";
        return resolve("app.storage.documents-root", "HRMS_DOCUMENTS_ROOT", fallback);
    }

    // ------------------------------------------------------------------
    // Database settings
    // ------------------------------------------------------------------

    public String dbUrl() {
        String url = resolve("db.url", "HRMS_DB_URL", null);
        if (url == null) {
            throw new ConfigurationException("Database URL is not configured (db.url)");
        }
        return url;
    }

    public String dbUsername() {
        return resolve("db.username", "HRMS_DB_USER", "root");
    }

    public String dbPassword() {
        return resolve("db.password", "HRMS_DB_PASSWORD", "");
    }

    public int poolMaxSize() {
        return intSetting("db.pool.max-size", 10);
    }

    public int poolMinIdle() {
        return intSetting("db.pool.min-idle", 2);
    }

    public int poolConnectionTimeoutSeconds() {
        return intSetting("db.pool.connection-timeout-seconds", 10);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Resolves a setting: dedicated environment variable first, then an
     * embedded {@code ${ENV_VAR}} placeholder, then the property, then the
     * default.
     */
    private String resolve(String key, String envVariable, String defaultValue) {
        if (envVariable != null) {
            String fromEnv = System.getenv(envVariable);
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv.trim();
            }
        }
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        value = value.trim();
        if (value.startsWith("${") && value.endsWith("}")) {
            String envName = value.substring(2, value.length() - 1);
            String fromEnv = System.getenv(envName);
            return fromEnv == null || fromEnv.isBlank() ? defaultValue : fromEnv.trim();
        }
        return value;
    }

    private int intSetting(String key, int defaultValue) {
        String raw = get(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Setting '" + key + "' is not a valid integer: " + raw);
        }
    }
}
