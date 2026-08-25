package com.ams.hrms.db;

import java.util.List;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.exception.DataAccessException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.MigrateOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies versioned database migrations from {@code classpath:db/migration}
 * using Flyway. On first run the target database is created automatically
 * (via the {@code createDatabaseIfNotExist} JDBC property) and
 * {@code V1__schema.sql} / {@code V2__seed.sql} are applied. Subsequent runs
 * validate the schema history and apply only new migrations, so schema drift
 * between installations is impossible.
 */
public final class DatabaseMigrator {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseMigrator.class);

    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    private DatabaseMigrator() {
    }

    /**
     * Runs pending migrations.
     *
     * @return the number of migrations that were applied in this run
     */
    public static int migrate(AppConfig config) {
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(config.dbUrl(), config.dbUsername(), config.dbPassword())
                    .locations(MIGRATION_LOCATION)
                    .baselineOnMigrate(true)
                    .load();

            MigrateResult result = flyway.migrate();

            List<MigrateOutput> applied = result.migrations == null ? List.of() : result.migrations;
            for (MigrateOutput migration : applied) {
                LOG.info("  applied V{} - {}", migration.version, migration.description);
            }
            LOG.info("Schema version is now '{}'", result.targetSchemaVersion == null ? "?" : result.targetSchemaVersion);
            return result.migrationsExecuted;

        } catch (FlywayException e) {
            throw new DataAccessException(
                    "Database migration failed: " + e.getMessage(),
                    "The database structure could not be prepared. Please check the log file for details.",
                    e);
        }
    }
}
