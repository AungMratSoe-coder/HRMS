package com.ams.hrms.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.exception.DataAccessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-startup connectivity probe. Verifies the MySQL server is reachable and
 * the credentials work, translating raw JDBC errors into clear, actionable
 * messages (server down vs. wrong password vs. unknown database) before the
 * connection pool or migrations are attempted.
 */
public final class DbChecker {

    private static final Logger LOG = LoggerFactory.getLogger(DbChecker.class);

    private static final Pattern URL_PATTERN =
            Pattern.compile("^jdbc:mysql://([^/:?]+)(?::(\\d+))?/([^?]+).*");

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int SOCKET_TIMEOUT_MS = 8_000;
    private static final int LOGIN_TIMEOUT_S = 8;

    /**
     * Result of a successful probe.
     *
     * @param host          MySQL host from the JDBC URL
     * @param port          MySQL port (default 3306 when absent)
     * @param database      target schema name
     * @param serverVersion MySQL server version string
     */
    public record PingResult(String host, int port, String database, String serverVersion) {
    }

    private DbChecker() {
    }

    /**
     * Attempts a lightweight {@code SELECT VERSION()} against the configured
     * server. Throws {@link DataAccessException} with a user-friendly message
     * on any failure.
     */
    public static PingResult check(AppConfig config) {
        String url = config.dbUrl();
        Matcher matcher = URL_PATTERN.matcher(url);

        String host = matcher.matches() ? matcher.group(1) : "unknown";
        int port = matcher.matches() && matcher.group(2) != null
                ? Integer.parseInt(matcher.group(2))
                : 3306;
        String database = matcher.matches() ? matcher.group(3) : "unknown";

        String probeUrl = url + (url.contains("?") ? "&" : "?")
                + "connectTimeout=" + CONNECT_TIMEOUT_MS
                + "&socketTimeout=" + SOCKET_TIMEOUT_MS;

        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_S);
        LOG.debug("Probing MySQL connectivity at {}:{}", host, port);

        try (Connection connection = DriverManager.getConnection(probeUrl, config.dbUsername(), config.dbPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {

            resultSet.next();
            return new PingResult(host, port, database, resultSet.getString(1));

        } catch (SQLTimeoutException e) {
            throw new DataAccessException(
                    "MySQL server at " + host + ":" + port + " did not respond within the timeout",
                    "Cannot reach the database server at " + host + ":" + port
                            + ". Please make sure MySQL is running.",
                    e);
        } catch (SQLException e) {
            throw translate(e, host, port);
        }
    }

    private static DataAccessException translate(SQLException e, String host, int port) {
        int errorCode = e.getErrorCode();
        String sqlState = e.getSQLState();

        if (errorCode == 1045 || errorCode == 1698 || "28000".equals(sqlState)) {
            return new DataAccessException(
                    "MySQL rejected the credentials (error " + errorCode + ")",
                    "Database login failed. Please check the configured database username and password.",
                    e);
        }
        if (errorCode == 1049) {
            return new DataAccessException(
                    "Unknown database '" + e.getMessage() + "'",
                    "The target database does not exist and could not be created automatically.",
                    e);
        }
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (message.contains("communications link") || message.contains("connection refused")) {
            return new DataAccessException(
                    "Cannot connect to MySQL at " + host + ":" + port,
                    "Cannot reach the database server at " + host + ":" + port
                            + ". Please make sure MySQL is running.",
                    e);
        }
        return new DataAccessException(
                "Database connectivity check failed (error " + errorCode + ", SQLState " + sqlState + ")",
                "A database connection problem occurred: " + firstLine(e.getMessage()),
                e);
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return newline > 0 ? text.substring(0, newline) : text;
    }
}
