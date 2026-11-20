package com.campusflow.repository;

import com.campusflow.model.Student;
import com.campusflow.model.Ticket;
import com.campusflow.model.User;
import com.campusflow.model.enums.Priority;
import com.campusflow.model.enums.Role;
import com.campusflow.model.enums.TicketStatus;
import com.campusflow.tql.TicketFilter;
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

@DisplayName("TicketRepository")
class TicketRepositoryTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_ticket_repo_test?mode=memory&cache=shared";
    private static final String DUMMY_HASH = "$2a$12$e8Y9X5J9H4Z3Q2W1E0R9T8Y7U6I5O4P3A2S1D0F9G8H7J6K5L4M3N";

    private TicketRepository ticketRepo;
    private UserRepository userRepo;

    private int student1Id;
    private int student2Id;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();

        ticketRepo = new TicketRepository();
        userRepo = new UserRepository();

        // Create two test students
        User s1 = userRepo.save(new Student(0, "student_one", DUMMY_HASH, "Student One", "s1@test.com", true, LocalDateTime.now()));
        User s2 = userRepo.save(new Student(0, "student_two", DUMMY_HASH, "Student Two", "s2@test.com", true, LocalDateTime.now()));

        student1Id = s1.getId();
        student2Id = s2.getId();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("save and findById persists and retrieves ticket with correct fields")
    void saveAndFindById() {
        LocalDateTime now = LocalDateTime.now();
        Ticket ticket = new Ticket.Builder()
                .title("Wi-Fi down in Lab 3")
                .description("Cannot connect to eduroam from computer 15")
                .priority(Priority.HIGH)
                .status(TicketStatus.OPEN)
                .categoryId(1)
                .submitterId(student1Id)
                .createdAt(now)
                .build();

        Ticket saved = ticketRepo.save(ticket);
        assertTrue(saved.getId() > 0);
        assertEquals("Wi-Fi down in Lab 3", saved.getTitle());
        assertEquals(Priority.HIGH, saved.getPriority());
        assertEquals(TicketStatus.OPEN, saved.getStatus());
        assertNull(saved.getAssignedStaffId());
        assertNull(saved.getResolvedAt());

        Optional<Ticket> retrieved = ticketRepo.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(saved.getId(), retrieved.get().getId());
        assertEquals("Wi-Fi down in Lab 3", retrieved.get().getTitle());
    }

    @Test
    @DisplayName("findBySubmitter returns only tickets submitted by specified user")
    void findBySubmitter() {
        Ticket t1 = new Ticket.Builder().title("T1").description("D1").priority(Priority.LOW).status(TicketStatus.OPEN).categoryId(1).submitterId(student1Id).createdAt(LocalDateTime.now()).build();
        Ticket t2 = new Ticket.Builder().title("T2").description("D2").priority(Priority.MEDIUM).status(TicketStatus.OPEN).categoryId(1).submitterId(student1Id).createdAt(LocalDateTime.now()).build();
        Ticket t3 = new Ticket.Builder().title("T3").description("D3").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(student2Id).createdAt(LocalDateTime.now()).build();

        ticketRepo.save(t1);
        ticketRepo.save(t2);
        ticketRepo.save(t3);

        List<Ticket> student1Tickets = ticketRepo.findBySubmitter(student1Id);
        assertEquals(2, student1Tickets.size());

        List<Ticket> student2Tickets = ticketRepo.findBySubmitter(student2Id);
        assertEquals(1, student2Tickets.size());
        assertEquals("T3", student2Tickets.get(0).getTitle());
    }

    @Test
    @DisplayName("update modifies status, assigned staff, and resolution timestamp")
    void updateTicket() {
        Ticket ticket = ticketRepo.save(new Ticket.Builder()
                .title("Initial Ticket")
                .description("Initial Description")
                .priority(Priority.MEDIUM)
                .status(TicketStatus.OPEN)
                .categoryId(1)
                .submitterId(student1Id)
                .createdAt(LocalDateTime.now())
                .build());

        LocalDateTime now = LocalDateTime.now();
        ticket.assignTo(1); // assign to admin (id 1)
        ticket.setStatus(TicketStatus.RESOLVED, now);

        ticketRepo.update(ticket);

        Optional<Ticket> retrieved = ticketRepo.findById(ticket.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(TicketStatus.RESOLVED, retrieved.get().getStatus());
        assertEquals(1, retrieved.get().getAssignedStaffId());
        assertNotNull(retrieved.get().getResolvedAt());
    }

    @Test
    @DisplayName("search with Role.STUDENT forcefully restricts results to student's tickets (ADR-002)")
    void searchStudentRestricted() {
        // student 1 creates 1 ticket, student 2 creates 1 ticket
        ticketRepo.save(new Ticket.Builder().title("S1 Ticket").description("D").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(student1Id).createdAt(LocalDateTime.now()).build());
        ticketRepo.save(new Ticket.Builder().title("S2 Ticket").description("D").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(student2Id).createdAt(LocalDateTime.now()).build());

        // Student 1 searches with no filters
        TicketFilter filter = new TicketFilter();
        List<Ticket> results = ticketRepo.search(filter, Role.STUDENT, student1Id);

        assertEquals(1, results.size());
        assertEquals("S1 Ticket", results.get(0).getTitle());

        // Student 1 attempts to query student 2's tickets by providing submitterUsername
        filter.setSubmitterUsername("student_two");
        List<Ticket> hackResults = ticketRepo.search(filter, Role.STUDENT, student1Id);

        assertEquals(1, hackResults.size(), "Student filter must not bypass ownership restriction");
        assertEquals("S1 Ticket", hackResults.get(0).getTitle());
    }

    @Test
    @DisplayName("search with Role.ADMIN searches across all tickets and orders by priority urgency")
    void searchAdminOrdering() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 20, 10, 0);

        // Save tickets with different priorities
        ticketRepo.save(new Ticket.Builder().title("Low Ticket").description("D").priority(Priority.LOW).status(TicketStatus.OPEN).categoryId(1).submitterId(student1Id).createdAt(base).build());
        ticketRepo.save(new Ticket.Builder().title("Critical Ticket").description("D").priority(Priority.CRITICAL).status(TicketStatus.OPEN).categoryId(1).submitterId(student2Id).createdAt(base.plusMinutes(5)).build());
        ticketRepo.save(new Ticket.Builder().title("High Ticket").description("D").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(student1Id).createdAt(base.plusMinutes(10)).build());

        TicketFilter filter = new TicketFilter();
        filter.setStatus("OPEN");

        List<Ticket> results = ticketRepo.search(filter, Role.ADMIN, 1);

        assertEquals(3, results.size());
        assertEquals("Critical Ticket", results.get(0).getTitle(), "CRITICAL priority must appear first");
        assertEquals("High Ticket", results.get(1).getTitle(), "HIGH priority must appear second");
        assertEquals("Low Ticket", results.get(2).getTitle(), "LOW priority must appear third");
    }

    @Test
    @DisplayName("search with empty TicketFilter executes valid SQL without token collision (regression)")
    void searchEmptyFilterRegression() {
        ticketRepo.save(new Ticket.Builder().title("Regr Ticket").description("D").priority(Priority.LOW).status(TicketStatus.OPEN).categoryId(1).submitterId(student1Id).createdAt(LocalDateTime.now()).build());

        TicketFilter emptyFilter = new TicketFilter();
        List<Ticket> results = ticketRepo.search(emptyFilter, Role.ADMIN, 1);
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }
}
