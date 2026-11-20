package com.campusflow.util;

import com.campusflow.server.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Manages SQLite database connection lifecycle, DDL schema migrations, and initial seed data.
 *
 * <h2>Concurrency & Connection Strategy (ADR-001, ADR-005, ADR-009)</h2>
 * <ul>
 *   <li><strong>Scoped Connection Factory:</strong> {@link #getConnection()} returns an independent
 *       connection per operation or transaction. Sharing a single connection across 20 worker threads
 *       causes statement and transaction interference.</li>
 *   <li><strong>WAL Mode:</strong> File-based connections configure {@code PRAGMA journal_mode = WAL;}
 *       enabling non-blocking concurrent readers alongside writers.</li>
 *   <li><strong>Busy Timeout:</strong> Every connection configures {@code PRAGMA busy_timeout = 5000;}
 *       so threads wait up to 5 seconds during write locks rather than throwing {@code SQLITE_BUSY}.</li>
 *   <li><strong>Foreign Keys:</strong> Enforces {@code PRAGMA foreign_keys = ON;} on every connection.</li>
 *   <li><strong>In-Memory Test Mode:</strong> When configured with shared-memory SQLite
 *       ({@code mode=memory&cache=shared}), a root connection is held open so the in-memory database
 *       persists across individual scoped connections until {@link #reset()} is called.</li>
 * </ul>
 *
 * <p>SCD concepts: SCD-12 (JDBC / SQLite), SCD-03 (Thread Safety), SCD-11 (Singleton / Factory).</p>
 */
public final class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_INITIAL_PASSWORD = "admin123";

    private static String configuredJdbcUrl = null;
    private static Connection rootInMemoryConnection = null;

    private DatabaseManager() {
        throw new UnsupportedOperationException("DatabaseManager is a static utility class");
    }

    // ── Configuration & Lifecycle ─────────────────────────────────────────────

    /**
     * Initializes or overrides the JDBC connection URL.
     * Used in tests to switch to an in-memory SQLite URL.
     *
     * @param jdbcUrl the SQLite JDBC URL (non-null, non-blank)
     */
    public static synchronized void init(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "JDBC URL must not be null");
        if (jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC URL must not be blank");
        }
        close();
        configuredJdbcUrl = jdbcUrl.trim();

        // If in-memory shared cache is used, hold a root connection open so it doesn't vanish
        if (isInMemoryUrl(configuredJdbcUrl)) {
            try {
                rootInMemoryConnection = DriverManager.getConnection(configuredJdbcUrl);
                try (Statement stmt = rootInMemoryConnection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                }
                log.info("Initialized in-memory SQLite database: {}", configuredJdbcUrl);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to open root in-memory connection: " + e.getMessage(), e);
            }
        } else {
            log.info("Configured file-based SQLite database: {}", configuredJdbcUrl);
        }
    }

    /**
     * Returns the active JDBC connection URL, defaulting to the path configured in {@link ServerConfig}.
     *
     * @return active JDBC URL
     */
    public static synchronized String getJdbcUrl() {
        if (configuredJdbcUrl == null) {
            String dbPath = ServerConfig.load().getDbPath();
            configuredJdbcUrl = "jdbc:sqlite:" + dbPath;
        }
        return configuredJdbcUrl;
    }

    /**
     * Resets database state and closes any open root connection.
     * Primarily used between unit test suites.
     */
    public static synchronized void reset() {
        close();
        configuredJdbcUrl = null;
    }

    /**
     * Closes the root in-memory connection if one is active.
     */
    public static synchronized void close() {
        if (rootInMemoryConnection != null) {
            try {
                if (!rootInMemoryConnection.isClosed()) {
                    rootInMemoryConnection.close();
                }
            } catch (SQLException e) {
                log.warn("Error closing root in-memory connection: {}", e.getMessage());
            } finally {
                rootInMemoryConnection = null;
            }
        }
    }

    // ── Connection Factory ───────────────────────────────────────────────────

    /**
     * Obtains a fresh, configured connection to the SQLite database.
     *
     * <p>The caller is responsible for closing the connection (typically using
     * {@code try-with-resources}). Every connection has foreign key constraints
     * and busy timeouts enabled.</p>
     *
     * @return a new, open {@link Connection}
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        String url = getJdbcUrl();
        Connection conn = DriverManager.getConnection(url);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            if (!isInMemoryUrl(url)) {
                stmt.execute("PRAGMA journal_mode = WAL;");
            }
        }

        return conn;
    }

    // ── Schema Initialization ────────────────────────────────────────────────

    /**
     * Creates all required tables and indexes if they do not already exist.
     *
     * @throws SQLException if DDL execution fails
     */
    public static synchronized void initialiseSchema() throws SQLException {
        log.info("Initialising SQLite schema...");

        String[] ddlStatements = {
            // Departments table
            """
            CREATE TABLE IF NOT EXISTS departments (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                name        TEXT    NOT NULL UNIQUE,
                description TEXT
            );
            """,

            // Users table
            """
            CREATE TABLE IF NOT EXISTS users (
                id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                username             TEXT    NOT NULL UNIQUE,
                password_hash        TEXT    NOT NULL,
                role                 TEXT    NOT NULL CHECK (UPPER(role) IN ('STUDENT', 'STAFF', 'ADMIN')),
                full_name            TEXT    NOT NULL,
                email                TEXT    NOT NULL UNIQUE,
                department_id        INTEGER REFERENCES departments(id) ON DELETE SET NULL,
                is_active            INTEGER NOT NULL DEFAULT 1,
                created_at           TEXT    NOT NULL,
                must_change_password INTEGER NOT NULL DEFAULT 0
            );
            """,

            // Categories table
            """
            CREATE TABLE IF NOT EXISTS categories (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                name          TEXT    NOT NULL UNIQUE,
                description   TEXT,
                department_id INTEGER NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
                is_active     INTEGER NOT NULL DEFAULT 1
            );
            """,

            // Tickets table
            """
            CREATE TABLE IF NOT EXISTS tickets (
                id                INTEGER PRIMARY KEY AUTOINCREMENT,
                title             TEXT    NOT NULL CHECK (LENGTH(title) BETWEEN 1 AND 150),
                description       TEXT    NOT NULL CHECK (LENGTH(description) BETWEEN 1 AND 2000),
                priority          TEXT    NOT NULL CHECK (UPPER(priority) IN ('LOW','MEDIUM','HIGH','CRITICAL')),
                status            TEXT    NOT NULL CHECK (UPPER(status) IN ('OPEN','IN_PROGRESS','ESCALATED','RESOLVED','CLOSED'))
                                          DEFAULT 'OPEN',
                category_id       INTEGER NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
                submitter_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
                assigned_staff_id INTEGER          REFERENCES users(id) ON DELETE SET NULL,
                created_at        TEXT    NOT NULL,
                updated_at        TEXT    NOT NULL,
                resolved_at       TEXT
            );
            """,

            // Ticket notes table
            """
            CREATE TABLE IF NOT EXISTS ticket_notes (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                ticket_id   INTEGER NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
                author_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
                note        TEXT    NOT NULL CHECK (LENGTH(note) >= 1),
                created_at  TEXT    NOT NULL
            );
            """,

            // Audit log table
            """
            CREATE TABLE IF NOT EXISTS audit_log (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                actor_id    INTEGER NOT NULL,
                action      TEXT    NOT NULL,
                entity_type TEXT    NOT NULL,
                entity_id   INTEGER NOT NULL,
                timestamp   TEXT    NOT NULL,
                details     TEXT
            );
            """,

            // Indexes for fast lookups
            "CREATE INDEX IF NOT EXISTS idx_tickets_submitter ON tickets (submitter_id);",
            "CREATE INDEX IF NOT EXISTS idx_tickets_assigned_staff ON tickets (assigned_staff_id);",
            "CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets (status);",
            "CREATE INDEX IF NOT EXISTS idx_tickets_priority ON tickets (priority);",
            "CREATE INDEX IF NOT EXISTS idx_tickets_category ON tickets (category_id);",
            "CREATE INDEX IF NOT EXISTS idx_ticket_notes_ticket ON ticket_notes (ticket_id);",
            "CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log (actor_id);",
            "CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log (entity_type, entity_id);"
        };

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                for (String ddl : ddlStatements) {
                    stmt.execute(ddl);
                }
                migrateSchema(conn);
                conn.commit();
                log.info("Database schema and indexes successfully initialised.");
            } catch (SQLException e) {
                conn.rollback();
                log.error("Failed to execute DDL migration, rolled back: {}", e.getMessage());
                throw e;
            }
        }
    }

    // ── Seed Data ────────────────────────────────────────────────────────────

    /**
     * Seeds initial departments, complaint categories, and the default administrator account
     * if the database tables are empty. Idempotent: safe to run multiple times.
     *
     * @throws SQLException if data seeding fails
     */
    public static synchronized void seedData() throws SQLException {
        log.info("Checking seed data requirements...");

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                seedDepartments(conn);
                seedCategories(conn);
                seedAdminUser(conn);
                conn.commit();
                log.info("Seed data verification completed.");
            } catch (SQLException e) {
                conn.rollback();
                log.error("Seed data transaction failed, rolled back: {}", e.getMessage());
                throw e;
            }
        }
    }

    private static void seedDepartments(Connection conn) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM departments";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Departments already seeded
            }
        }

        String insertSql = "INSERT OR IGNORE INTO departments (id, name, description) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            // Department 1: IT
            ps.setInt(1, 1);
            ps.setString(2, "IT");
            ps.setString(3, "Information Technology — network, software, hardware");
            ps.addBatch();

            // Department 2: Maintenance
            ps.setInt(1, 2);
            ps.setString(2, "Maintenance");
            ps.setString(3, "Facilities and physical infrastructure");
            ps.addBatch();

            // Department 3: Academic
            ps.setInt(1, 3);
            ps.setString(2, "Academic");
            ps.setString(3, "Academic administration and student services");
            ps.addBatch();

            ps.executeBatch();
            log.info("Seeded 3 default departments: IT, Maintenance, Academic.");
        }
    }

    private static void seedCategories(Connection conn) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM categories";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Categories already seeded
            }
        }

        String insertSql = "INSERT OR IGNORE INTO categories (name, description, department_id, is_active) VALUES (?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            // IT categories (dept 1)
            addCategoryBatch(ps, "Network Issue", "Wi-Fi, LAN, VPN problems", 1);
            addCategoryBatch(ps, "Software Problem", "Licensed software, lab computers", 1);
            addCategoryBatch(ps, "Hardware Fault", "Computers, printers, projectors", 1);

            // Maintenance categories (dept 2)
            addCategoryBatch(ps, "Room Maintenance", "Broken fixtures, cleaning issues", 2);
            addCategoryBatch(ps, "Electrical Fault", "Lighting, sockets, power failures", 2);
            addCategoryBatch(ps, "Plumbing", "Water supply, drainage issues", 2);

            // Academic categories (dept 3)
            addCategoryBatch(ps, "Exam Query", "Exam schedule, results, re-checks", 3);
            addCategoryBatch(ps, "Enrollment Issue", "Registration, course add/drop", 3);
            addCategoryBatch(ps, "Document Request", "Transcripts, letters, certificates", 3);

            ps.executeBatch();
            log.info("Seeded 9 default categories across 3 departments.");
        }
    }

    private static void addCategoryBatch(PreparedStatement ps, String name, String desc, int deptId) throws SQLException {
        ps.setString(1, name);
        ps.setString(2, desc);
        ps.setInt(3, deptId);
        ps.addBatch();
    }

    private static void seedAdminUser(Connection conn) throws SQLException {
        String checkAdminSql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkAdminSql)) {
            ps.setString(1, DEFAULT_ADMIN_USERNAME);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return; // Admin user already exists
                }
            }
        }

        String insertUserSql = """
            INSERT INTO users (username, password_hash, role, full_name, email, department_id, is_active, created_at, must_change_password)
            VALUES (?, ?, ?, ?, ?, NULL, 1, ?, 1)
            """;

        String adminHash = PasswordUtil.hash(DEFAULT_ADMIN_INITIAL_PASSWORD);
        String nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        try (PreparedStatement ps = conn.prepareStatement(insertUserSql)) {
            ps.setString(1, DEFAULT_ADMIN_USERNAME);
            ps.setString(2, adminHash);
            ps.setString(3, "ADMIN");
            ps.setString(4, "System Administrator");
            ps.setString(5, "admin@campusflow.local");
            ps.setString(6, nowIso);
            ps.executeUpdate();
        }

        // Record initial security audit log entry advising password change on first login
        String auditSql = """
            INSERT INTO audit_log (actor_id, action, entity_type, entity_id, timestamp, details)
            VALUES (1, 'CREATE', 'USER', 1, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(auditSql)) {
            ps.setString(1, nowIso);
            ps.setString(2, "{\"note\":\"Default administrator account seeded. Mandatory password change required on first login.\"}");
            ps.executeUpdate();
        }

        log.info("Default administrator account seeded (username: '{}'). Password change on first login recommended.",
                 DEFAULT_ADMIN_USERNAME);
    }

    private static void migrateSchema(Connection conn) throws SQLException {
        boolean hasMustChangePassword = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(users)")) {
            while (rs.next()) {
                String colName = rs.getString("name");
                if ("must_change_password".equalsIgnoreCase(colName)) {
                    hasMustChangePassword = true;
                    break;
                }
            }
        }

        if (!hasMustChangePassword) {
            log.info("Migrating schema: adding 'must_change_password' column to users table...");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE users ADD COLUMN must_change_password INTEGER NOT NULL DEFAULT 0;");
            }
            log.info("Schema migration completed: 'must_change_password' column added.");
        }
    }

    // ── Helper Methods ────────────────────────────────────────────────────────

    private static boolean isInMemoryUrl(String url) {
        return url != null && (url.contains(":memory:") || url.contains("mode=memory"));
    }
}
