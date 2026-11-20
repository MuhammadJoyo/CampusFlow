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

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TicketNoteRepository")
class TicketNoteRepositoryTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_note_repo_test?mode=memory&cache=shared";
    private TicketNoteRepository noteRepo;
    private TicketRepository ticketRepo;

    private int testTicketId;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();

        noteRepo = new TicketNoteRepository();
        ticketRepo = new TicketRepository();

        // Create a parent ticket for testing notes (submitter 1 = admin, category 1 = Network Issue)
        Ticket parentTicket = new Ticket.Builder()
                .title("Note Parent Ticket")
                .description("Parent ticket for note tests")
                .priority(Priority.HIGH)
                .status(TicketStatus.OPEN)
                .categoryId(1)
                .submitterId(1)
                .createdAt(LocalDateTime.now())
                .build();

        Ticket saved = ticketRepo.save(parentTicket);
        testTicketId = saved.getId();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("save and findById persists and retrieves a ticket note")
    void saveAndFindById() {
        LocalDateTime now = LocalDateTime.now();
        TicketNote note = new TicketNote(0, testTicketId, 1, "Investigating Wi-Fi router issues.", now);

        TicketNote saved = noteRepo.save(note);
        assertTrue(saved.getId() > 0);
        assertEquals(testTicketId, saved.getTicketId());
        assertEquals(1, saved.getAuthorId());
        assertEquals("Investigating Wi-Fi router issues.", saved.getNote());

        Optional<TicketNote> retrieved = noteRepo.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("Investigating Wi-Fi router issues.", retrieved.get().getNote());
    }

    @Test
    @DisplayName("findByTicketId returns notes in chronological order")
    void findByTicketIdChronological() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 20, 10, 0);

        TicketNote n1 = new TicketNote(0, testTicketId, 1, "Initial note 1", base);
        TicketNote n2 = new TicketNote(0, testTicketId, 1, "Follow-up note 2", base.plusMinutes(15));
        TicketNote n3 = new TicketNote(0, testTicketId, 1, "Resolution note 3", base.plusMinutes(30));

        noteRepo.save(n1);
        noteRepo.save(n2);
        noteRepo.save(n3);

        List<TicketNote> notes = noteRepo.findByTicketId(testTicketId);
        assertEquals(3, notes.size());
        assertEquals("Initial note 1", notes.get(0).getNote());
        assertEquals("Follow-up note 2", notes.get(1).getNote());
        assertEquals("Resolution note 3", notes.get(2).getNote());
    }

    @Test
    @DisplayName("save note with non-existent ticketId fails foreign key check")
    void nonExistentTicketThrows() {
        TicketNote orphan = new TicketNote(0, 99999, 1, "Orphan Note text", LocalDateTime.now());
        assertThrows(RepositoryException.class, () -> noteRepo.save(orphan));
    }

    @Test
    @DisplayName("save note with non-existent authorId fails foreign key check")
    void nonExistentAuthorThrows() {
        TicketNote invalidAuthor = new TicketNote(0, testTicketId, 88888, "Invalid author Note text", LocalDateTime.now());
        assertThrows(RepositoryException.class, () -> noteRepo.save(invalidAuthor));
    }
}
