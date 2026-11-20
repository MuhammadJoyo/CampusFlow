package com.campusflow.repository;

import com.campusflow.model.Ticket;
import com.campusflow.model.TicketNote;
import com.campusflow.model.enums.Priority;
import com.campusflow.model.enums.TicketStatus;
import com.campusflow.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies transactional execution across multiple repositories sharing an injected {@link Connection}.
 */
@DisplayName("Repository Transaction Coordination")
class RepositoryTransactionTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_tx_test?mode=memory&cache=shared";

    private TicketRepository ticketRepo;
    private TicketNoteRepository noteRepo;
    private AuditLogRepository auditRepo;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();

        ticketRepo = new TicketRepository();
        noteRepo = new TicketNoteRepository();
        auditRepo = new AuditLogRepository();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("Atomic multi-repository transaction commits all changes successfully")
    void multiRepositoryAtomicCommit() throws SQLException {
        int createdTicketId;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Save Ticket
                Ticket ticket = new Ticket.Builder()
                        .title("Transactional Submission")
                        .description("Testing atomicity across repositories")
                        .priority(Priority.HIGH)
                        .status(TicketStatus.OPEN)
                        .categoryId(1)
                        .submitterId(1)
                        .createdAt(LocalDateTime.now())
                        .build();

                Ticket savedTicket = ticketRepo.save(ticket, conn);
                createdTicketId = savedTicket.getId();

                // 2. Save Initial Timeline Note
                TicketNote note = new TicketNote(0, createdTicketId, 1, "Initial submission note", LocalDateTime.now());
                noteRepo.save(note, conn);

                // 3. Save Audit Log Record
                auditRepo.log(1, "CREATE", "TICKET", createdTicketId, "Ticket created with note", conn);

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }

        // Verify across independent connections that all three records were persisted
        assertTrue(ticketRepo.findById(createdTicketId).isPresent(), "Ticket must be persisted");
        assertEquals(1, noteRepo.findByTicketId(createdTicketId).size(), "Ticket note must be persisted");
        assertEquals(1, auditRepo.findByEntity("TICKET", createdTicketId).size(), "Audit log must be persisted");
    }

    @Test
    @DisplayName("Transaction rollback on failure leaves zero orphaned records")
    void multiRepositoryRollback() throws SQLException {
        int initialTicketCount = ticketRepo.findAll().size();
        int initialAuditCount = auditRepo.findAll().size();

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Save Ticket
                Ticket ticket = new Ticket.Builder()
                        .title("Failed Submission")
                        .description("This will be rolled back")
                        .priority(Priority.LOW)
                        .status(TicketStatus.OPEN)
                        .categoryId(1)
                        .submitterId(1)
                        .createdAt(LocalDateTime.now())
                        .build();

                Ticket saved = ticketRepo.save(ticket, conn);

                // 2. Trigger deliberate failure (e.g. foreign key violation on note)
                TicketNote invalidNote = new TicketNote(0, 999999, 1, "Invalid note referencing non-existent ticket", LocalDateTime.now());
                noteRepo.save(invalidNote, conn); // Fails FK constraint

                conn.commit();
                fail("Transaction should have failed on invalid foreign key");
            } catch (SQLException expected) {
                conn.rollback();
            }
        }

        // Verify zero orphaned tickets or audit records were left
        assertEquals(initialTicketCount, ticketRepo.findAll().size(), "Ticket count must remain unchanged after rollback");
        assertEquals(initialAuditCount, auditRepo.findAll().size(), "Audit log count must remain unchanged after rollback");
    }
}
