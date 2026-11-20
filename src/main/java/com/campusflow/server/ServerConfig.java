package com.campusflow.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Immutable server configuration loaded from {@code server.properties} on the classpath.
 *
 * <h2>Configuration Keys (ADR-005)</h2>
 * <pre>
 *   server.port                    = 9090
 *   server.thread.pool.size        = 20
 *   server.session.timeout.minutes = 30
 *   server.db.path                 = campusflow.db
 * </pre>
 *
 * <p>All values are loaded once at startup via {@link #load()} and are immutable
 * thereafter. No code path may modify configuration at runtime.</p>
 *
 * <p>Use: {@code ServerConfig config = ServerConfig.load();}</p>
 *
 * <p>SCD concepts: SCD-11 (factory method, immutability), NFR-09 (runtime-configurable
 * without recompilation).</p>
 */
public final class ServerConfig {

    private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);

    // ── Property Keys ────────────────────────────────────────────────────────

    private static final String PROPERTIES_FILE    = "server.properties";
    private static final String KEY_PORT           = "server.port";
    private static final String KEY_POOL_SIZE      = "server.thread.pool.size";
    private static final String KEY_SESSION_TIMEOUT = "server.session.timeout.minutes";
    private static final String KEY_DB_PATH        = "server.db.path";

    // ── Default Values (ADR-005) ─────────────────────────────────────────────

    private static final int    DEFAULT_PORT             = 9090;
    private static final int    DEFAULT_POOL_SIZE         = 20;
    private static final int    DEFAULT_SESSION_TIMEOUT   = 30;
    private static final String DEFAULT_DB_PATH           = "campusflow.db";

    // ── Fields ───────────────────────────────────────────────────────────────

    private final int    port;
    private final int    threadPoolSize;
    private final int    sessionTimeoutMinutes;
    private final String dbPath;

    // ── Constructor (private) ─────────────────────────────────────────────────

    private ServerConfig(int port, int threadPoolSize, int sessionTimeoutMinutes, String dbPath) {
        this.port                  = port;
        this.threadPoolSize        = threadPoolSize;
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        this.dbPath                = dbPath;
    }

    // ── Factory Method ───────────────────────────────────────────────────────

    /**
     * Loads {@code server.properties} from the classpath and returns an immutable
     * {@code ServerConfig}. Missing keys fall back to documented defaults (ADR-005).
     *
     * @return fully populated, immutable ServerConfig
     * @throws IllegalStateException if {@code server.properties} cannot be found on classpath
     */
    public static ServerConfig load() {
        Properties props = new Properties();

        try (InputStream is = ServerConfig.class
                .getClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {

            if (is == null) {
                throw new IllegalStateException(
                    "server.properties not found on classpath. " +
                    "Ensure src/main/resources/server.properties exists.");
            }
            props.load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read server.properties: " + e.getMessage(), e);
        }

        int    port    = parseIntOrDefault(props, KEY_PORT,            DEFAULT_PORT);
        int    pool    = parseIntOrDefault(props, KEY_POOL_SIZE,       DEFAULT_POOL_SIZE);
        int    timeout = parseIntOrDefault(props, KEY_SESSION_TIMEOUT, DEFAULT_SESSION_TIMEOUT);
        String dbPath  = props.getProperty(KEY_DB_PATH, DEFAULT_DB_PATH).trim();

        log.info("ServerConfig loaded: port={}, poolSize={}, sessionTimeout={}min, dbPath={}",
                 port, pool, timeout, dbPath);

        return new ServerConfig(port, pool, timeout, dbPath);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** @return TCP port the server listens on (default 9090) */
    public int getPort() { return port; }

    /** @return fixed thread pool size for handling client connections (default 20) */
    public int getThreadPoolSize() { return threadPoolSize; }

    /** @return session inactivity timeout in minutes (default 30) */
    public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }

    /** @return path to the SQLite database file (default campusflow.db) */
    public String getDbPath() { return dbPath; }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int parseIntOrDefault(Properties props, String key, int defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            log.warn("Property '{}' not found in server.properties, using default: {}", key, defaultValue);
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer for property '{}': '{}', using default: {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    // ── Object overrides ─────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "ServerConfig{port=" + port +
               ", threadPoolSize=" + threadPoolSize +
               ", sessionTimeoutMinutes=" + sessionTimeoutMinutes +
               ", dbPath='" + dbPath + "'}";
    }
}
