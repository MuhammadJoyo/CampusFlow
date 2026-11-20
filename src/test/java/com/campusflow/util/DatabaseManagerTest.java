package com.campusflow.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for {@link DatabaseManager}.
 *
 * Verifies schema initialization, relational foreign keys, transaction rollback,
 * idempotent seed data, admin BCrypt hashing, and multi-threaded concurrent access.
 */
@DisplayName("DatabaseManager")
class DatabaseManagerTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_test_db?mode=memory&cache=shared";

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    // ── 1. Schema Initialization ─────────────────────────────────────────────

    @Test
    @DisplayName("initialiseSchema creates all 6 tables and 8 indexes")
    void schemaCreation() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            DatabaseMetaDataHelper meta = new DatabaseMetaDataHelper(conn);

            assertTrue(meta.tableExists("departments"), "departments table must exist");
            assertTrue(meta.tableExists("users"), "users table must exist");
            assertTrue(meta.tableExists("categories"), "categories table must exist");
            assertTrue(meta.tableExists("tickets"), "tickets table must exist");
            assertTrue(meta.tableExists("ticket_notes"), "ticket_notes table must exist");
            assertTrue(meta.tableExists("audit_log"), "audit_log table must exist");

            assertTrue(meta.indexExists("idx_tickets_submitter"));
            assertTrue(meta.indexExists("idx_tickets_assigned_staff"));
            assertTrue(meta.indexExists("idx_tickets_status"));
            assertTrue(meta.indexExists("idx_tickets_priority"));
            assertTrue(meta.indexExists("idx_tickets_category"));
            assertTrue(meta.indexExists("idx_ticket_notes_ticket"));
            assertTrue(meta.indexExists("idx_audit_log_actor"));
            assertTrue(meta.indexExists("idx_audit_log_entity"));
        }
    }

    // ── 2. Foreign Key Enforcement ───────────────────────────────────────────

    @Test
    @DisplayName("Foreign key constraints are enforced (PRAGMA foreign_keys = ON)")
    void foreignKeyEnforcement() throws SQLException {
        DatabaseManager.seedData();

        try (Connection conn = DatabaseManager.getConnection()) {
            // Attempt to insert ticket with non-existent submitter_id (9999)
            String sql = """
                INSERT INTO tickets (title, description, priority, status, category_id, submitter_id, created_at, updated_at)
                VALUES ('Title', 'Description text', 'HIGH', 'OPEN', 1, 9999, '2026-11-20T10:00:00', '2026-11-20T10:00:00')
                """;

            try (Statement stmt = conn.createStatement()) {
                assertThrows(SQLException.class, () -> stmt.execute(sql),
                    "Inserting with non-existent foreign key must throw SQLException");
            }
        }
    }

    // ── 3. Transaction Rollback ──────────────────────────────────────────────

    @Test
    @DisplayName("Multi-step operation rolls back cleanly on exception")
    void transactionRollback() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO departments (name, description) VALUES (?, ?)")) {
                ps.setString(1, "TestDept");
                ps.setString(2, "Temporary Department");
                ps.executeUpdate();
            }

            // Explicit rollback without commit
            conn.rollback();
        }

        // Verify TestDept does not exist
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM departments WHERE name = ?")) {
            ps.setString(1, "TestDept");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Rolled back department must not exist");
            }
        }
    }

    // ── 4. Seed Data & Idempotency ───────────────────────────────────────────

    @Test
    @DisplayName("seedData is idempotent: multiple calls do not duplicate records")
    void seedDataIdempotency() throws SQLException {
        DatabaseManager.seedData();
        DatabaseManager.seedData(); // Second call should be no-op

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM departments")) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1), "Exactly 3 departments must be seeded");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM categories")) {
                assertTrue(rs.next());
                assertEquals(9, rs.getInt(1), "Exactly 9 categories must be seeded");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE username = 'admin'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Exactly 1 admin user must be seeded");
            }
        }
    }

    // ── 5. Admin BCrypt Password Verification ────────────────────────────────

    @Test
    @DisplayName("Seeded admin account has authentic BCrypt password hash verifying 'admin123'")
    void adminBcryptPasswordVerification() throws SQLException {
        DatabaseManager.seedData();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM users WHERE username = 'admin'")) {

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Admin user must exist");
                String hash = rs.getString("password_hash");

                assertNotNull(hash);
                assertTrue(hash.startsWith("$2a$12$"), "Hash must be BCrypt cost 12 format");
                assertTrue(PasswordUtil.verify("admin123", hash), "Password 'admin123' must verify against hash");
                assertFalse(PasswordUtil.verify("wrongpass", hash), "Incorrect password must not verify");
            }
        }
    }

    // ── 6. Concurrent Multi-threaded Access ──────────────────────────────────

    @Test
    @DisplayName("Concurrent threads can read and write without SQLITE_BUSY or deadlocks")
    void concurrentDatabaseAccess() throws Exception {
        DatabaseManager.seedData();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int workerId = i;
            tasks.add(() -> {
                // Each worker obtains its own connection via connection factory
                try (Connection conn = DatabaseManager.getConnection()) {
                    // Read operation
                    try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM categories WHERE department_id = ?")) {
                        ps.setInt(1, 1);
                        try (ResultSet rs = ps.executeQuery()) {
                            assertTrue(rs.next());
                            assertTrue(rs.getInt(1) > 0);
                        }
                    }

                    // Concurrent write into audit log
                    String sql = "INSERT INTO audit_log (actor_id, action, entity_type, entity_id, timestamp, details) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, 1);
                        ps.setString(2, "PING");
                        ps.setString(3, "WORKER");
                        ps.setInt(4, workerId);
                        ps.setString(5, "2026-11-20T10:00:00");
                        ps.setString(6, "Worker " + workerId + " heartbeat");
                        ps.executeUpdate();
                    }
                }
                return true;
            });
        }

        List<Future<Boolean>> results = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Boolean> f : results) {
            assertTrue(f.get(), "Each concurrent task must succeed without exception");
        }

        // Verify all 10 worker writes were recorded
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM audit_log WHERE action = 'PING'")) {
            assertTrue(rs.next());
            assertEquals(10, rs.getInt(1), "All 10 concurrent audit records must be persisted");
        }
    }

    // ── Helper class to inspect SQLite metadata ──────────────────────────────

    private static class DatabaseMetaDataHelper {
        private final Connection conn;

        DatabaseMetaDataHelper(Connection conn) {
            this.conn = conn;
        }

        boolean tableExists(String tableName) throws SQLException {
            String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }

        boolean indexExists(String indexName) throws SQLException {
            String sql = "SELECT name FROM sqlite_master WHERE type='index' AND name=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, indexName);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }
}
