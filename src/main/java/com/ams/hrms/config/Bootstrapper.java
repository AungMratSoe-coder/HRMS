package com.ams.hrms.config;

import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.db.DbChecker.PingResult;
import com.ams.hrms.exception.HrmsException;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.ui.theme.ThemeManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates application startup:
 * <ol>
 *   <li>load configuration</li>
 *   <li>probe MySQL connectivity with friendly diagnostics</li>
 *   <li>initialize the connection pool</li>
 *   <li>apply Flyway migrations (creates the database on first run)</li>
 *   <li>wire services, install the theme, open the login window</li>
 * </ol>
 */
public final class Bootstrapper {

    private static final Logger LOG = LoggerFactory.getLogger(Bootstrapper.class);

    public void launch(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(DatabaseConfig::close, "hrms-shutdown"));
        LOG.info("============================================================");
        try {
            AppConfig config = AppConfig.get();
            LOG.info("{} v{} - starting", config.appName(), config.appVersion());

            PingResult ping = DbChecker.check(config);
            LOG.info("Connected to MySQL {} at {}:{} (database '{}')",
                    ping.serverVersion(), ping.host(), ping.port(), ping.database());

            DatabaseConfig.initialize(config);

            int applied = DatabaseMigrator.migrate(config);

            long tableCount = new Sql().count(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()");

            LOG.info("------------------------------------------------------------");
            LOG.info("  Database            : {}:{}/{}", ping.host(), ping.port(), ping.database());
            LOG.info("  Migrations applied  : {}", applied);
            LOG.info("  Tables              : {}", tableCount);
            LOG.info("  Status              : READY");
            LOG.info("============================================================");

            ServiceRegistry.initialize();
            ThemeManager.install();

            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    new com.ams.hrms.ui.login.LoginFrame().setVisible(true);
                    LOG.info("Login window opened");
                } catch (Exception e) {
                    LOG.error("Failed to open the login window", e);
                    System.exit(3);
                }
            });

        } catch (HrmsException e) {
            LOG.error("Startup failed: {}", e.getMessage());
            LOG.debug("Startup failure detail", e);
            System.err.println("[STARTUP FAILED] " + e.getUserMessage());
            System.exit(1);
        } catch (Exception e) {
            LOG.error("Unexpected failure during startup", e);
            System.err.println("[STARTUP FAILED] Unexpected error: " + e.getMessage());
            System.exit(2);
        }
    }
}
