package com.campusflow.util;

import com.campusflow.model.User;
import com.campusflow.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Database Migration & Schema Evolution")
class DatabaseMigrationTest {

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("Fresh database initialization includes must_change_password column and seeds admin with flag=1")
    void freshDatabaseHasMustChangePassword() throws SQLException {
        String dbUrl = "jdbc:sqlite:file:cf_migration_fresh?mode=memory&cache=shared";
        DatabaseManager.init(dbUrl);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();

        // Check schema has column
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(users)")) {
            boolean found = false;
            while (rs.next()) {
                if ("must_change_password".equalsIgnoreCase(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Column must_change_password must exist in users table");
        }

        // Verify seeded admin user has must_change_password == true
        UserRepository userRepo = new UserRepository();
        Optional<User> admin = userRepo.findByUsername("admin");
        assertTrue(admin.isPresent());
        assertTrue(admin.get().isMustChangePassword(), "Seeded admin must have mustChangePassword = true");
    }

    @Test
    @DisplayName("Migration detects existing legacy users table without must_change_password and applies ALTER TABLE seamlessly")
    void migrationOnExistingLegacyDatabase() throws SQLException {
        String legacyDbUrl = "jdbc:sqlite:file:cf_migration_legacy?mode=memory&cache=shared";
        DatabaseManager.init(legacyDbUrl);

        // Simulate legacy database schema before Module 4 (without must_change_password)
        try (Connection conn = DriverManager.getConnection(legacyDbUrl)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE users (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT,
                        username        TEXT    NOT NULL UNIQUE,
                        password_hash   TEXT    NOT NULL,
                        role            TEXT    NOT NULL,
                        full_name       TEXT    NOT NULL,
                        email           TEXT    NOT NULL UNIQUE,
                        department_id   INTEGER,
                        is_active       INTEGER NOT NULL DEFAULT 1,
                        created_at      TEXT    NOT NULL
                    );
                    """);

                stmt.execute("""
                    INSERT INTO users (username, password_hash, role, full_name, email, is_active, created_at)
                    VALUES ('legacy_user', 'dummy_hash', 'STUDENT', 'Legacy User', 'legacy@test.com', 1, '2026-11-20T10:00:00');
                    """);
            }
        }

        // Run initialiseSchema which must detect legacy table and apply ALTER TABLE
        DatabaseManager.initialiseSchema();

        // Verify column now exists
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(users)")) {
            boolean found = false;
            while (rs.next()) {
                if ("must_change_password".equalsIgnoreCase(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Migration must have added must_change_password column via ALTER TABLE");
        }

        // Verify legacy user data was preserved and defaulted to must_change_password = 0 (false)
        UserRepository userRepo = new UserRepository();
        Optional<User> legacy = userRepo.findByUsername("legacy_user");
        assertTrue(legacy.isPresent());
        assertEquals("Legacy User", legacy.get().getFullName());
        assertFalse(legacy.get().isMustChangePassword(), "Pre-existing users should default to mustChangePassword = false");
    }
}
