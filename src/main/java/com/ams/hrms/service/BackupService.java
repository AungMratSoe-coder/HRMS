package com.ams.hrms.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;

/**
 * Database backup &amp; restore through the MySQL command-line clients
 * ({@code mysqldump} / {@code mysql}). Both operations require
 * {@link Permissions#SETTINGS_MANAGE} and are audited.
 *
 * <p>Credentials are passed to the tools through a temporary
 * {@code --defaults-extra-file} options file instead of the command line,
 * so passwords never appear in process listings and need no shell quoting.
 * The temporary file is deleted when the operation finishes.</p>
 *
 * <p>{@link #restoreFrom(Path)} closes the connection pool while the client
 * runs and reopens it afterwards; it is intended as an exclusive
 * administration action (no other users working in the application).</p>
 */
public class BackupService {

    /** Audit category for backup operations. */
    public static final String AUDIT_CATEGORY = "SYSTEM";

    public static final String MYSQLDUMP_PATH_KEY = "backup.mysqldump_path";
    public static final String MYSQL_PATH_KEY = "backup.mysql_path";

    private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);

    /** jdbc:mysql://host[:port]/dbname[?params] */
    private static final Pattern JDBC_URL =
            Pattern.compile("^jdbc:mysql://([^:/?#]+)(?::(\\d+))?/([^?]+)");

    private final AuditService auditService;

    public BackupService(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Dumps the whole database (structure + data + routines/triggers) to
     * {@code targetFile}. Consistent snapshot via {@code --single-transaction}.
     *
     * @return the written file (same as the argument, for caller messaging)
     */
    public Path backupTo(Path targetFile) {
        SecurityService.require(Permissions.SETTINGS_MANAGE);
        requireTargetWritable(targetFile);

        String database = connection().database();
        Path written = runWithClientOptions(options -> {
            List<String> command = List.of(
                    resolveTool(MYSQLDUMP_PATH_KEY, "mysqldump"),
                    "--defaults-extra-file=" + options,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    "--default-character-set=utf8mb4",
                    database);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectOutput(targetFile.toFile());
            Process process = start(builder, "mysqldump");
            String stderr = readStderr(process);
            int exit = waitFor(process);
            if (exit != 0 || !isNonEmpty(targetFile)) {
                deleteQuietly(targetFile);
                throw failure("backup", stderr);
            }
            auditService.record("BACKUP", AUDIT_CATEGORY, "Database", null,
                    "Database backed up to '" + targetFile + "'");
            LOG.info("Database '{}' backed up to {} ({} bytes)",
                    database, targetFile, sizeOf(targetFile));
            return targetFile;
        });
        return written;
    }

    /**
     * Restores the database from a previously created SQL dump. The
     * connection pool is closed first and reopened afterwards regardless of
     * the outcome.
     */
    public void restoreFrom(Path dumpFile) {
        SecurityService.require(Permissions.SETTINGS_MANAGE);
        requireDumpReadable(dumpFile);

        String database = connection().database();
        DatabaseConfig.close();
        try {
            runWithClientOptions(options -> {
                List<String> command = List.of(
                        resolveTool(MYSQL_PATH_KEY, "mysql"),
                        "--defaults-extra-file=" + options,
                        "--default-character-set=utf8mb4",
                        database);
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectInput(dumpFile.toFile());
                Process process = start(builder, "mysql");
                String stderr = readStderr(process);
                int exit = waitFor(process);
                if (exit != 0) {
                    throw failure("restore", stderr);
                }
                return true;
            });
            auditService.record("RESTORE", AUDIT_CATEGORY, "Database", null,
                    "Database restored from '" + dumpFile.getFileName() + "'");
            LOG.info("Database '{}' restored from {}", database, dumpFile);
        } finally {
            DatabaseConfig.initialize(AppConfig.get());
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Writes a temporary [client] options file and runs the work with it. */
    private <T> T runWithClientOptions(ClientOptionsWork<T> work) {
        ConnectionInfo info = connection();
        Path optionsFile = null;
        try {
            optionsFile = Files.createTempFile("hrms-client-", ".cnf");
            Files.writeString(optionsFile, "[client]\n"
                    + "user=" + info.username() + "\n"
                    + "password=" + escape(info.password()) + "\n"
                    + "host=" + info.host() + "\n"
                    + "port=" + info.port() + "\n",
                    StandardCharsets.UTF_8);
            return work.run(optionsFile.toString());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    "MySQL tool invocation failed: " + e.getMessage(),
                    "The database tool could not be run: " + e.getMessage());
        } finally {
            if (optionsFile != null) {
                deleteQuietly(optionsFile);
            }
        }
    }

    @FunctionalInterface
    private interface ClientOptionsWork<T> {
        T run(String optionsFilePath) throws Exception;
    }

    /** Resolves a tool: the configured absolute path, else the system PATH. */
    private String resolveTool(String settingKey, String fallbackCommand) {
        String configured = new Sql().first(
                "SELECT setting_value FROM app_settings WHERE setting_key = ?",
                rs -> rs.getString(1), settingKey).orElse("");
        return configured == null || configured.isBlank()
                ? fallbackCommand : configured.trim();
    }

    /**
     * Parses host/port/name/user/password out of the running configuration.
     */
    private ConnectionInfo connection() {
        AppConfig config = AppConfig.get();
        Matcher matcher = JDBC_URL.matcher(config.dbUrl());
        if (!matcher.find()) {
            throw new BusinessException(
                    "Unsupported JDBC URL: " + config.dbUrl(),
                    "The database address format is not recognized. "
                            + "Only jdbc:mysql://host[:port]/dbname is supported.");
        }
        return new ConnectionInfo(matcher.group(1),
                matcher.group(2) == null ? 3306 : Integer.parseInt(matcher.group(2)),
                matcher.group(3), config.dbUsername(), config.dbPassword());
    }

    private record ConnectionInfo(String host, int port, String database,
                                  String username, String password) {
    }

    /** Escapes a value for a MySQL option file (backslash and quote aware). */
    private static String escape(String rawValue) {
        return rawValue.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Process start(ProcessBuilder builder, String toolName) {
        try {
            return builder.start();
        } catch (IOException e) {
            throw new BusinessException(
                    toolName + " could not be started: " + e.getMessage(),
                    "'" + toolName + "' was not found. Install the MySQL client "
                            + "tools or set the full path in Settings > General.");
        }
    }

    private static int waitFor(Process process) {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new BusinessException(
                    "Interrupted while waiting for the MySQL tool",
                    "The operation was interrupted.");
        }
    }

    /** Reads the tool's stderr (bounded) for error reporting. */
    private static String readStderr(Process process) {
        try (var stream = process.getErrorStream()) {
            byte[] bytes = stream.readAllBytes();
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            return text.length() <= 800 ? text : text.substring(text.length() - 800);
        } catch (IOException e) {
            return "";
        }
    }

    private static void requireTargetWritable(Path targetFile) {
        if (targetFile == null || targetFile.toString().isBlank()) {
            throw new BusinessException(
                    "Empty backup target path",
                    "Choose a location for the backup file first.");
        }
        if (Files.isDirectory(targetFile)) {
            throw new BusinessException(
                    "Backup target is a directory: " + targetFile,
                    "The selected backup location is a folder. Choose a .sql file name.");
        }
    }

    private static void requireDumpReadable(Path dumpFile) {
        if (dumpFile == null || !Files.isRegularFile(dumpFile)) {
            throw new BusinessException(
                    "Dump file not found: " + dumpFile,
                    "Choose an existing .sql backup file first.");
        }
        if (!isNonEmpty(dumpFile)) {
            throw new BusinessException(
                    "Dump file is empty: " + dumpFile,
                    "That backup file is empty and cannot be restored.");
        }
    }

    private static BusinessException failure(String operation, String stderr) {
        String detail = stderr == null || stderr.isBlank()
                ? "no details were reported" : stderr;
        LOG.error("MySQL {} failed: {}", operation, detail);
        return new BusinessException(
                "MySQL " + operation + " failed: " + detail,
                "The database " + operation + " did not finish. Details:\n"
                        + lastLines(detail, 3));
    }

    private static String lastLines(String text, int count) {
        String[] lines = text.split("\\R");
        StringBuilder shown = new StringBuilder();
        for (int i = Math.max(0, lines.length - count); i < lines.length; i++) {
            if (!shown.isEmpty()) {
                shown.append('\n');
            }
            shown.append(lines[i]);
        }
        return shown.toString();
    }

    private static boolean isNonEmpty(Path path) {
        try {
            return Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort cleanup only.
        }
    }
}
