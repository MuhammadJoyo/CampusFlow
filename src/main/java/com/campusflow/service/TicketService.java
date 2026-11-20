package com.campusflow.service;

import com.campusflow.model.Department;
import com.campusflow.model.Ticket;
import com.campusflow.model.TicketNote;
import com.campusflow.model.User;
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
import com.campusflow.service.exception.ResourceNotFoundException;
import com.campusflow.service.exception.ServiceException;
import com.campusflow.service.exception.ValidationException;
import com.campusflow.tql.TicketFilter;
import com.campusflow.util.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Core business service managing ticket workflows, state machine transitions,
 * queue coordination, and server-side authorization (SRS FR-03..09, FR-12, FR-15, FR-17).
 */
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepo;
    private final TicketNoteRepository noteRepo;
    private final DepartmentRepository deptRepo;
    private final CategoryRepository categoryRepo;
    private final UserRepository userRepo;
    private final TicketQueueManager queueManager;
    private final RoutingService routingService;
    private final AuditLogRepository auditRepo;
    private final AnalyticsService analyticsService;

    public TicketService(TicketRepository ticketRepo,
                         TicketNoteRepository noteRepo,
                         DepartmentRepository deptRepo,
                         CategoryRepository categoryRepo,
                         UserRepository userRepo,
                         TicketQueueManager queueManager,
                         RoutingService routingService,
                         AuditLogRepository auditRepo,
                         AnalyticsService analyticsService) {

        this.ticketRepo = Objects.requireNonNull(ticketRepo, "ticketRepo must not be null");
        this.noteRepo = Objects.requireNonNull(noteRepo, "noteRepo must not be null");
        this.deptRepo = Objects.requireNonNull(deptRepo, "deptRepo must not be null");
        this.categoryRepo = Objects.requireNonNull(categoryRepo, "categoryRepo must not be null");
        this.userRepo = Objects.requireNonNull(userRepo, "userRepo must not be null");
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager must not be null");
        this.routingService = Objects.requireNonNull(routingService, "routingService must not be null");
        this.auditRepo = Objects.requireNonNull(auditRepo, "auditRepo must not be null");
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService must not be null");
    }

    /**
     * Submits a new ticket complaint (FR-03, UC-02).
     */
    public Ticket submitTicket(Session studentSession, TicketSubmitDto dto) {
        Objects.requireNonNull(studentSession, "studentSession must not be null");
        studentSession.requireRole(Role.STUDENT);
        Objects.requireNonNull(dto, "dto must not be null");

        // Validate target department via category
        Department dept = routingService.getDepartmentForCategory(dto.categoryId());

        LocalDateTime now = LocalDateTime.now();
        Ticket ticket = new Ticket.Builder()
                .title(dto.title())
                .description(dto.description())
                .priority(dto.priority())
                .status(TicketStatus.OPEN)
                .categoryId(dto.categoryId())
                .submitterId(studentSession.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build();

        Ticket savedTicket;
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                savedTicket = ticketRepo.save(ticket, conn);
                auditRepo.log(studentSession.getUserId(), "CREATE", "TICKET", savedTicket.getId(),
                        "Submitted complaint in department " + dept.getName(), conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new ServiceException("Failed to persist ticket submission: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new ServiceException("Database connection error: " + e.getMessage(), e);
        }

        // Post-commit: enqueue and invalidate analytics cache
        queueManager.enqueue(savedTicket, dept.getName());
        analyticsService.invalidateCache();

        log.info("Ticket #{} submitted by student ID {} and routed to {} queue.",
                savedTicket.getId(), studentSession.getUserId(), dept.getName());
        return savedTicket;
    }

    /**
     * Claims the next available highest-priority ticket for the staff member (FR-06, UC-05).
     * Implements thread-safe atomic conditional assignment.
     */
    public Optional<Ticket> claimNextTicket(Session staffSession) {
        Objects.requireNonNull(staffSession, "staffSession must not be null");
        staffSession.requireRole(Role.STAFF);

        Integer staffDeptId = staffSession.getDepartmentId();
        if (staffDeptId == null) {
            throw new AuthorizationException("Staff member has no assigned department.");
        }

        Department dept = deptRepo.findById(staffDeptId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + staffDeptId));

        while (true) {
            Optional<Ticket> opt = queueManager.dequeue(dept.getName());
            if (opt.isEmpty()) {
                return Optional.empty();
            }

            Ticket candidate = opt.get();

            // Attempt atomic conditional assignment in SQLite
            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    String sql = """
                        UPDATE tickets
                        SET status = 'IN_PROGRESS', assigned_staff_id = ?, updated_at = ?
                        WHERE id = ? AND status IN ('OPEN', 'ESCALATED')
                        """;

                    int rowsUpdated;
                    String nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, staffSession.getUserId());
                        ps.setString(2, nowIso);
                        ps.setInt(3, candidate.getId());
                        rowsUpdated = ps.executeUpdate();
                    }

                    if (rowsUpdated == 0) {
                        // Parallel actor (e.g. Admin) modified or assigned this ticket. Rollback and continue.
                        conn.rollback();
                        log.debug("Ticket #{} claim collided; polling next candidate.", candidate.getId());
                        continue;
                    }

                    auditRepo.log(staffSession.getUserId(), "ASSIGN", "TICKET", candidate.getId(),
                            "Staff member claimed ticket from " + dept.getName() + " queue", conn);
                    conn.commit();

                    candidate.assignTo(staffSession.getUserId());
                    candidate.setStatus(TicketStatus.IN_PROGRESS, LocalDateTime.now());
                    analyticsService.invalidateCache();

                    log.info("Ticket #{} claimed by staff member '{}' (dept: {}).",
                            candidate.getId(), staffSession.getUsername(), dept.getName());
                    return Optional.of(candidate);

                } catch (SQLException e) {
                    conn.rollback();
                    // Put ticket back in queue to prevent data loss
                    queueManager.enqueue(candidate, dept.getName());
                    throw new ServiceException("Database error during ticket claim: " + e.getMessage(), e);
                }
            } catch (SQLException e) {
                queueManager.enqueue(candidate, dept.getName());
                throw new ServiceException("Database connection error: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Manually assigns a ticket to a staff member (Admin override, FR-06, UC-11).
     */
    public void assignTicket(Session adminSession, int ticketId, int targetStaffId) {
        Objects.requireNonNull(adminSession, "adminSession must not be null");
        adminSession.requireRole(Role.ADMIN);

        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + ticketId));

        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) {
            throw new InvalidStateTransitionException("Cannot assign ticket in " + ticket.getStatus() + " status.");
        }

        User staff = userRepo.findById(targetStaffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found with ID: " + targetStaffId));

        if (staff.getRole() != Role.STAFF || !staff.isActive()) {
            throw new ValidationException("Target user must be an active Staff member.");
        }

        Department dept = routingService.getDepartmentForCategory(ticket.getCategoryId());
        if (((com.campusflow.model.StaffMember) staff).getDepartmentId() != dept.getId()) {
            throw new ValidationException("Staff member does not belong to the ticket's department (" + dept.getName() + ").");
        }

        LocalDateTime now = LocalDateTime.now();
        ticket.assignTo(targetStaffId);
        ticket.setStatus(TicketStatus.IN_PROGRESS, now);

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ticketRepo.update(ticket, conn);
                auditRepo.log(adminSession.getUserId(), "ASSIGN", "TICKET", ticketId,
                        "Admin assigned ticket to staff user '" + staff.getUsername() + "'", conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new ServiceException("Failed to update ticket assignment: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new ServiceException("Database connection error: " + e.getMessage(), e);
        }

        queueManager.remove(ticketId, dept.getName());
        analyticsService.invalidateCache();
        log.info("Ticket #{} assigned to staff member '{}' by Admin.", ticketId, staff.getUsername());
    }

    /**
     * Updates ticket status (e.g. RESOLVED) with mandatory note enforcement (FR-07, FR-15, UC-06).
     */
    public void updateStatus(Session actorSession, int ticketId, TicketStatus newStatus, String note) {
        Objects.requireNonNull(actorSession, "actorSession must not be null");
        actorSession.requireRole(Role.STAFF, Role.ADMIN);

        if (newStatus == TicketStatus.ESCALATED) {
            escalateTicket(actorSession, ticketId, note);
            return;
        }

        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + ticketId));

        Department dept = routingService.getDepartmentForCategory(ticket.getCategoryId());

        // Staff constraints
        if (actorSession.getRole() == Role.STAFF) {
            if (ticket.getAssignedStaffId() == null || ticket.getAssignedStaffId() != actorSession.getUserId()) {
                throw new AuthorizationException("Staff members can only update tickets assigned to themselves.");
            }
            actorSession.requireDepartment(dept.getId());

            // FR-07: Note min 10 characters for staff status updates
            if (note == null || note.trim().length() < 10) {
                throw new ValidationException("Status update note must be at least 10 characters in length.");
            }
        }

        // Validate allowed state transitions
        validateStateTransition(ticket.getStatus(), newStatus);

        LocalDateTime now = LocalDateTime.now();
        if (newStatus == TicketStatus.RESOLVED) {
            ticket.setStatus(TicketStatus.RESOLVED, now);
        } else if (newStatus == TicketStatus.CLOSED) {
            actorSession.requireRole(Role.ADMIN);
            ticket.setStatus(TicketStatus.CLOSED, now);
        } else {
            ticket.setStatus(newStatus, now);
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ticketRepo.update(ticket, conn);

                if (note != null && !note.isBlank()) {
                    TicketNote ticketNote = new TicketNote(0, ticketId, actorSession.getUserId(), note.trim(), now);
                    noteRepo.save(ticketNote, conn);
                }

                auditRepo.log(actorSession.getUserId(), newStatus.name(), "TICKET", ticketId,
                        "Status updated to " + newStatus, conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new ServiceException("Failed to update ticket status: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new ServiceException("Database connection error: " + e.getMessage(), e);
        }

        if (newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.CLOSED) {
            queueManager.remove(ticketId, dept.getName());
        }
        analyticsService.invalidateCache();
        log.info("Ticket #{} status transitioned to {} by {}.", ticketId, newStatus, actorSession.getUsername());
    }

    /**
     * Escalates a ticket, bumps priority, unassigns staff, and re-enqueues (FR-08, UC-07).
     */
    public void escalateTicket(Session staffSession, int ticketId, String reason) {
        Objects.requireNonNull(staffSession, "staffSession must not be null");
        staffSession.requireRole(Role.STAFF);

        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + ticketId));

        if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException("Only IN_PROGRESS tickets can be escalated. Current: " + ticket.getStatus());
        }

        if (ticket.getAssignedStaffId() == null || ticket.getAssignedStaffId() != staffSession.getUserId()) {
            throw new AuthorizationException("Only the assigned staff member can escalate this ticket.");
        }

        Department dept = routingService.getDepartmentForCategory(ticket.getCategoryId());
        staffSession.requireDepartment(dept.getId());

        if (reason == null || reason.trim().length() < 10) {
            throw new ValidationException("Escalation reason note must be at least 10 characters in length.");
        }

        Priority bumpedPriority = ticket.getPriority().bump();
        LocalDateTime now = LocalDateTime.now();

        // Construct escalated ticket state: status -> ESCALATED, priority bumped, assignedStaffId -> null
        Ticket escalatedTicket = new Ticket.Builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .priority(bumpedPriority)
                .status(TicketStatus.ESCALATED)
                .categoryId(ticket.getCategoryId())
                .submitterId(ticket.getSubmitterId())
                .assignedStaffId(null)
                .createdAt(ticket.getCreatedAt())
                .updatedAt(now)
                .resolvedAt(ticket.getResolvedAt())
                .build();

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ticketRepo.update(escalatedTicket, conn);

                TicketNote note = new TicketNote(0, ticketId, staffSession.getUserId(), "ESCALATION: " + reason.trim(), now);
                noteRepo.save(note, conn);

                auditRepo.log(staffSession.getUserId(), "ESCALATE", "TICKET", ticketId,
                        "Escalated with priority bump to " + bumpedPriority + ". Reason: " + reason.trim(), conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new ServiceException("Failed to escalate ticket: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new ServiceException("Database connection error: " + e.getMessage(), e);
        }

        // Re-enqueue into department priority queue with elevated priority
        queueManager.enqueue(escalatedTicket, dept.getName());
        analyticsService.invalidateCache();
        log.info("Ticket #{} escalated to {} with priority {} by staff '{}'.",
                ticketId, TicketStatus.ESCALATED, bumpedPriority, staffSession.getUsername());
    }

    /**
     * Closes a resolved ticket (Admin only, FR-15).
     */
    public void closeTicket(Session adminSession, int ticketId, String reason) {
        updateStatus(adminSession, ticketId, TicketStatus.CLOSED,
                (reason != null && !reason.isBlank()) ? reason : "Ticket closed by administrator");
    }

    /**
     * Retrieves a ticket with role-based access validation (FR-04, UC-04).
     */
    public Optional<Ticket> getTicket(Session session, int ticketId) {
        Objects.requireNonNull(session, "session must not be null");
        session.requireNotRequiringPasswordChange();

        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + ticketId));

        if (session.getRole() == Role.STUDENT) {
            if (ticket.getSubmitterId() != session.getUserId()) {
                throw new AuthorizationException("Students can only view their own tickets.");
            }
        } else if (session.getRole() == Role.STAFF) {
            Department dept = routingService.getDepartmentForCategory(ticket.getCategoryId());
            session.requireDepartment(dept.getId());
        }

        return Optional.of(ticket);
    }

    /**
     * Retrieves all timeline notes for a ticket.
     */
    public List<TicketNote> getTicketNotes(Session session, int ticketId) {
        getTicket(session, ticketId); // Enforces access authorization
        return noteRepo.findByTicketId(ticketId);
    }

    /**
     * Retrieves all tickets submitted by the authenticated student (FR-04, UC-03).
     */
    public List<Ticket> getMyTickets(Session studentSession) {
        Objects.requireNonNull(studentSession, "studentSession must not be null");
        studentSession.requireRole(Role.STUDENT);
        return ticketRepo.findBySubmitter(studentSession.getUserId());
    }

    /**
     * Retrieves tickets assigned to the authenticated staff member (FR-04, UC-05).
     */
    public List<Ticket> getAssignedTickets(Session staffSession) {
        Objects.requireNonNull(staffSession, "staffSession must not be null");
        staffSession.requireRole(Role.STAFF);
        return ticketRepo.findByAssignedStaff(staffSession.getUserId());
    }

    /**
     * Retrieves all tickets in the system (Admin only).
     */
    public List<Ticket> getAllTickets(Session adminSession) {
        Objects.requireNonNull(adminSession, "adminSession must not be null");
        adminSession.requireRole(Role.ADMIN);
        return ticketRepo.findAll();
    }

    /**
     * Searches tickets enforcing server-side scoping based on session role (FR-12, ADR-002, UC-08).
     */
    public List<Ticket> searchTickets(Session session, TicketFilter filter) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(filter, "filter must not be null");
        session.requireNotRequiringPasswordChange();

        if (session.getRole() == Role.STUDENT) {
            return ticketRepo.search(filter, Role.STUDENT, session.getUserId());
        } else if (session.getRole() == Role.STAFF) {
            Department dept = deptRepo.findById(session.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff department not found."));
            // Forcefully override department filter with staff member's department
            filter.setDepartment(dept.getName());
            return ticketRepo.search(filter, Role.STAFF, session.getUserId());
        } else {
            return ticketRepo.search(filter, Role.ADMIN, session.getUserId());
        }
    }

    private void validateStateTransition(TicketStatus current, TicketStatus target) {
        boolean valid = switch (current) {
            case OPEN -> target == TicketStatus.IN_PROGRESS;
            case IN_PROGRESS -> target == TicketStatus.RESOLVED || target == TicketStatus.ESCALATED;
            case ESCALATED -> target == TicketStatus.IN_PROGRESS;
            case RESOLVED -> target == TicketStatus.CLOSED;
            case CLOSED -> false; // Closed is terminal
        };

        if (!valid) {
            throw new InvalidStateTransitionException("Illegal status transition from '" + current + "' to '" + target + "'.");
        }
    }
}
