package com.campusflow.service;

import com.campusflow.model.Category;
import com.campusflow.model.Department;
import com.campusflow.model.StaffMember;
import com.campusflow.model.Student;
import com.campusflow.model.Ticket;
import com.campusflow.model.enums.Priority;
import com.campusflow.model.enums.Role;
import com.campusflow.model.enums.TicketStatus;
import com.campusflow.repository.AuditLogRepository;
import com.campusflow.repository.CategoryRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.TicketNoteRepository;
import com.campusflow.repository.TicketRepository;
import com.campusflow.repository.UserRepository;
import com.campusflow.service.dto.TicketSubmitDto;
import com.campusflow.service.exception.AuthorizationException;
import com.campusflow.service.exception.InvalidStateTransitionException;
import com.campusflow.service.exception.ValidationException;
import com.campusflow.tql.TicketFilter;
import com.campusflow.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TicketService")
class TicketServiceTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_ticket_service_test?mode=memory&cache=shared";
    private static final String DUMMY_HASH = "$2a$12$e8Y9X5J9H4Z3Q2W1E0R9T8Y7U6I5O4P3A2S1D0F9G8H7J6K5L4M3N";

    private TicketService ticketService;
    private TicketRepository ticketRepo;
    private TicketNoteRepository noteRepo;
    private DepartmentRepository deptRepo;
    private CategoryRepository categoryRepo;
    private UserRepository userRepo;
    private TicketQueueManager queueManager;
    private RoutingService routingService;
    private AuditLogRepository auditRepo;
    private AnalyticsService analyticsService;

    private Session student1Session;
    private Session student2Session;
    private Session itStaffSession;
    private Session maintStaffSession;
    private Session adminSession;

    private int student1Id;
    private int student2Id;
    private int itStaffId;
    private int maintStaffId;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();

        ticketRepo = new TicketRepository();
        noteRepo = new TicketNoteRepository();
        deptRepo = new DepartmentRepository();
        categoryRepo = new CategoryRepository();
        userRepo = new UserRepository();
        auditRepo = new AuditLogRepository();

        queueManager = new TicketQueueManager();
        queueManager.initialise(deptRepo.findAll());

        routingService = new RoutingService(categoryRepo, deptRepo);
        analyticsService = new AnalyticsService(ticketRepo, categoryRepo, deptRepo, userRepo);

        ticketService = new TicketService(ticketRepo, noteRepo, deptRepo, categoryRepo, userRepo,
                queueManager, routingService, auditRepo, analyticsService);

        // Create test users
        LocalDateTime now = LocalDateTime.now();
        student1Id = userRepo.save(new Student(0, "s1", DUMMY_HASH, "Student One", "s1@test.com", true, now)).getId();
        student2Id = userRepo.save(new Student(0, "s2", DUMMY_HASH, "Student Two", "s2@test.com", true, now)).getId();
        itStaffId = userRepo.save(new StaffMember(0, "it_staff", DUMMY_HASH, "IT Tech", "it@test.com", true, now, 1)).getId();
        maintStaffId = userRepo.save(new StaffMember(0, "maint_staff", DUMMY_HASH, "Maint Tech", "maint@test.com", true, now, 2)).getId();

        student1Session = new Session("sess-s1", student1Id, "s1", Role.STUDENT, null, false);
        student2Session = new Session("sess-s2", student2Id, "s2", Role.STUDENT, null, false);
        itStaffSession = new Session("sess-it", itStaffId, "it_staff", Role.STAFF, 1, false);
        maintStaffSession = new Session("sess-maint", maintStaffId, "maint_staff", Role.STAFF, 2, false);
        adminSession = new Session("sess-admin", 1, "admin", Role.ADMIN, null, false);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("submitTicket persists ticket in DB, writes audit log, and enqueues into department queue")
    void submitTicketSuccess() {
        TicketSubmitDto dto = new TicketSubmitDto("Wi-Fi down", "Cannot connect in lab 3", 1, Priority.HIGH);
        Ticket ticket = ticketService.submitTicket(student1Session, dto);

        assertTrue(ticket.getId() > 0);
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertEquals(student1Id, ticket.getSubmitterId());
        assertNull(ticket.getAssignedStaffId());

        // Verify in DB and queue
        assertTrue(ticketRepo.findById(ticket.getId()).isPresent());
        assertEquals(1, queueManager.size("IT"));
        assertFalse(auditRepo.findByEntity("TICKET", ticket.getId()).isEmpty());
    }

    @Test
    @DisplayName("claimNextTicket claims top ticket for staff's department and sets IN_PROGRESS")
    void claimNextTicketSuccess() {
        TicketSubmitDto dto = new TicketSubmitDto("Wi-Fi down", "Lab 3 issue", 1, Priority.HIGH);
        Ticket submitted = ticketService.submitTicket(student1Session, dto);

        Optional<Ticket> claimed = ticketService.claimNextTicket(itStaffSession);
        assertTrue(claimed.isPresent());
        assertEquals(submitted.getId(), claimed.get().getId());
        assertEquals(itStaffId, claimed.get().getAssignedStaffId());

        // Verify in DB that status is IN_PROGRESS and assigned_staff_id is itStaffId
        Ticket inDb = ticketRepo.findById(submitted.getId()).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, inDb.getStatus());
        assertEquals(itStaffId, inDb.getAssignedStaffId());
        assertEquals(0, queueManager.size("IT"));
    }

    @Test
    @DisplayName("Concurrent ticket claims by multiple staff members guarantee no double assignment")
    void concurrentTicketClaims() throws InterruptedException {
        // Enqueue 1 ticket in IT queue
        TicketSubmitDto dto = new TicketSubmitDto("Single Ticket", "Only one can claim", 1, Priority.CRITICAL);
        ticketService.submitTicket(student1Session, dto);

        // Two staff members in IT racing to claim
        Session itStaff2Session = new Session("sess-it-2", 99, "it_staff2", Role.STAFF, 1, false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        List<Ticket> claimedList = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                startLatch.await();
                ticketService.claimNextTicket(itStaffSession).ifPresent(claimedList::add);
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                ticketService.claimNextTicket(itStaff2Session).ifPresent(claimedList::add);
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly one staff member must have claimed the ticket
        assertEquals(1, claimedList.size(), "Only one staff member should have successfully claimed the ticket");
        assertEquals(0, queueManager.size("IT"));
    }

    @Test
    @DisplayName("Staff updating status with note < 10 characters throws ValidationException (FR-07)")
    void noteTooShortThrows() {
        Ticket ticket = ticketService.submitTicket(student1Session, new TicketSubmitDto("T", "D", 1, Priority.HIGH));
        ticketService.claimNextTicket(itStaffSession);

        assertThrows(ValidationException.class, () ->
                ticketService.updateStatus(itStaffSession, ticket.getId(), TicketStatus.RESOLVED, "Too short"));
    }

    @Test
    @DisplayName("Staff cannot update ticket assigned to another staff member")
    void staffCannotUpdateOthersTicket() {
        Ticket ticket = ticketService.submitTicket(student1Session, new TicketSubmitDto("T", "D", 1, Priority.HIGH));
        ticketService.claimNextTicket(itStaffSession);

        // Maintenance staff member attempts to update IT ticket
        assertThrows(AuthorizationException.class, () ->
                ticketService.updateStatus(maintStaffSession, ticket.getId(), TicketStatus.RESOLVED, "Resolution note text"));
    }

    @Test
    @DisplayName("Complete escalation workflow bumps priority, unassigns staff, and re-enqueues for re-claim")
    void escalationWorkflow() {
        // Submit MEDIUM priority ticket
        Ticket ticket = ticketService.submitTicket(student1Session, new TicketSubmitDto("T", "D", 1, Priority.MEDIUM));
        ticketService.claimNextTicket(itStaffSession);

        // Staff escalates ticket with note
        ticketService.escalateTicket(itStaffSession, ticket.getId(), "Cannot resolve locally, needs vendor support");

        // Verify state in DB
        Ticket inDb = ticketRepo.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.ESCALATED, inDb.getStatus());
        assertEquals(Priority.HIGH, inDb.getPriority(), "Priority must be bumped from MEDIUM to HIGH");
        assertNull(inDb.getAssignedStaffId(), "Assigned staff should be cleared for pool re-claim");

        // Verify ticket was re-enqueued in IT queue
        assertEquals(1, queueManager.size("IT"));
        assertEquals(ticket.getId(), queueManager.peek("IT").orElseThrow().getId());

        // A staff member claims the escalated ticket
        Optional<Ticket> reClaimed = ticketService.claimNextTicket(itStaffSession);
        assertTrue(reClaimed.isPresent());
        assertEquals(ticket.getId(), reClaimed.get().getId());
        assertEquals(TicketStatus.IN_PROGRESS, ticketRepo.findById(ticket.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Invalid state transitions throw InvalidStateTransitionException")
    void invalidStateTransitions() {
        Ticket ticket = ticketService.submitTicket(student1Session, new TicketSubmitDto("T", "D", 1, Priority.HIGH));

        // Claim ticket so itStaff is authenticated and assigned to the ticket (status becomes IN_PROGRESS)
        ticketService.claimNextTicket(itStaffSession);

        // IN_PROGRESS cannot transition back to OPEN
        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.updateStatus(itStaffSession, ticket.getId(), TicketStatus.OPEN, "Reverting to open is invalid"));

        // IN_PROGRESS cannot transition directly to CLOSED
        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.updateStatus(itStaffSession, ticket.getId(), TicketStatus.CLOSED, "Closing from in-progress is invalid"));
    }

    @Test
    @DisplayName("Admin can assign ticket to staff in the same department")
    void adminAssignTicket() {
        Ticket ticket = ticketService.submitTicket(student1Session, new TicketSubmitDto("T", "D", 1, Priority.HIGH));
        assertEquals(1, queueManager.size("IT"));

        ticketService.assignTicket(adminSession, ticket.getId(), itStaffId);

        Ticket inDb = ticketRepo.findById(ticket.getId()).orElseThrow();
        assertEquals(itStaffId, inDb.getAssignedStaffId());
        assertEquals(TicketStatus.IN_PROGRESS, inDb.getStatus());
        assertEquals(0, queueManager.size("IT"), "Assigned ticket must be removed from queue");
    }

    @Test
    @DisplayName("Admin assigning ticket to staff in different department throws ValidationException")
    void adminAssignWrongDepartmentThrows() {
        Ticket ticket = ticketService.submitTicket(student1Session, new TicketSubmitDto("T", "D", 1, Priority.HIGH));

        // Maint staff member in dept 2 assigned to IT ticket in dept 1
        assertThrows(ValidationException.class, () ->
                ticketService.assignTicket(adminSession, ticket.getId(), maintStaffId));
    }

    @Test
    @DisplayName("searchTickets enforces server-side scoping: Student sees only own; Staff sees only own dept")
    void searchTicketsAuthorizationScoping() {
        // Student 1 submits 1 ticket in IT
        ticketService.submitTicket(student1Session, new TicketSubmitDto("S1 IT Ticket", "D", 1, Priority.HIGH));

        // Student 2 submits 1 ticket in Maintenance
        ticketService.submitTicket(student2Session, new TicketSubmitDto("S2 Maint Ticket", "D", 4, Priority.HIGH));

        // Student 1 searches with no filters
        List<Ticket> s1Results = ticketService.searchTickets(student1Session, new TicketFilter());
        assertEquals(1, s1Results.size());
        assertEquals("S1 IT Ticket", s1Results.get(0).getTitle());

        // Student 1 attempts to query student 2's tickets
        TicketFilter hackFilter = new TicketFilter();
        hackFilter.setSubmitterUsername("s2");
        List<Ticket> hackResults = ticketService.searchTickets(student1Session, hackFilter);
        assertEquals(1, hackResults.size());
        assertEquals("S1 IT Ticket", hackResults.get(0).getTitle(), "Student cannot bypass ownership filter");

        // IT staff member searches
        List<Ticket> itStaffResults = ticketService.searchTickets(itStaffSession, new TicketFilter());
        assertEquals(1, itStaffResults.size());
        assertEquals("S1 IT Ticket", itStaffResults.get(0).getTitle(), "Staff member sees only own department tickets");

        // Admin searches with no filter (global visibility)
        List<Ticket> adminResults = ticketService.searchTickets(adminSession, new TicketFilter());
        assertEquals(2, adminResults.size(), "Admin has global visibility across all departments");
    }
}
