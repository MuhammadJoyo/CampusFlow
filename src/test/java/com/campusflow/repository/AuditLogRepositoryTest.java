package com.campusflow.repository;

import com.campusflow.model.AuditLog;
import com.campusflow.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuditLogRepository")
class AuditLogRepositoryTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_audit_repo_test?mode=memory&cache=shared";
    private AuditLogRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();
        repo = new AuditLogRepository();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("log persists an audit entry and findAll retrieves it")
    void logAndFindAll() {
        repo.log(1, "LOGIN", "USER", 1, "Admin login successful");

        List<AuditLog> logs = repo.findAll();
        assertFalse(logs.isEmpty());

        AuditLog latest = logs.get(0);
        assertEquals(1, latest.getActorId());
        assertEquals("LOGIN", latest.getAction());
        assertEquals("USER", latest.getEntityType());
        assertEquals(1, latest.getEntityId());
        assertEquals("Admin login successful", latest.getDetails());
    }

    @Test
    @DisplayName("findByEntity filters entries by entityType and entityId")
    void findByEntity() {
        repo.log(1, "CREATE", "TICKET", 101, "Ticket created");
        repo.log(1, "ASSIGN", "TICKET", 101, "Assigned to tech");
        repo.log(1, "CREATE", "TICKET", 202, "Other ticket created");

        List<AuditLog> ticket101Logs = repo.findByEntity("TICKET", 101);
        assertEquals(2, ticket101Logs.size());

        List<AuditLog> ticket202Logs = repo.findByEntity("TICKET", 202);
        assertEquals(1, ticket202Logs.size());
    }

    @Test
    @DisplayName("AuditLogRepository has no update or delete methods (purely append-only)")
    void repositoryIsAppendOnly() {
        boolean hasMutationMethod = Arrays.stream(AuditLogRepository.class.getMethods())
                .anyMatch(m -> m.getName().startsWith("update") || m.getName().startsWith("delete") || m.getName().startsWith("remove"));

        assertFalse(hasMutationMethod, "AuditLogRepository must be append-only with no update/delete methods");
    }
}
