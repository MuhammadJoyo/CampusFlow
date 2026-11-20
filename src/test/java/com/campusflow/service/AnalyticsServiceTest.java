package com.campusflow.service;

import com.campusflow.model.Student;
import com.campusflow.model.Ticket;
import com.campusflow.model.enums.Priority;
import com.campusflow.model.enums.Role;
import com.campusflow.model.enums.TicketStatus;
import com.campusflow.repository.CategoryRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.TicketRepository;
import com.campusflow.repository.UserRepository;
import com.campusflow.service.dto.AnalyticsReportDto;
import com.campusflow.service.exception.AuthorizationException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnalyticsService")
class AnalyticsServiceTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_analytics_test?mode=memory&cache=shared";

    private AnalyticsService analyticsService;
    private TicketRepository ticketRepo;
    private CategoryRepository categoryRepo;
    private DepartmentRepository deptRepo;
    private UserRepository userRepo;

    private Session adminSession;
    private Session studentSession;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();

        ticketRepo = new TicketRepository();
        categoryRepo = new CategoryRepository();
        deptRepo = new DepartmentRepository();
        userRepo = new UserRepository();

        analyticsService = new AnalyticsService(ticketRepo, categoryRepo, deptRepo, userRepo);

        adminSession = new Session("admin-session-id", 1, "admin", Role.ADMIN, null, false);
        studentSession = new Session("student-session-id", 2, "student", Role.STUDENT, null, false);

        // Seed some sample tickets
        LocalDateTime base = LocalDateTime.of(2026, 11, 20, 10, 0);

        // Open ticket
        ticketRepo.save(new Ticket.Builder().title("T1").description("D").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(base).build());

        // Resolved ticket (resolved in 2 hours)
        Ticket resolved = new Ticket.Builder().title("T2").description("D").priority(Priority.MEDIUM).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(base).build();
        Ticket savedResolved = ticketRepo.save(resolved);
        savedResolved.setStatus(TicketStatus.RESOLVED, base.plusHours(2));
        ticketRepo.update(savedResolved);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("Admin can retrieve computed analytics metrics")
    void computeReport() {
        AnalyticsReportDto report = analyticsService.getReport(adminSession);
        assertNotNull(report);
        assertEquals(1, report.ticketsByStatus().get("OPEN"));
        assertEquals(1, report.ticketsByStatus().get("RESOLVED"));
        assertEquals(2, report.ticketsByCategory().get("Network Issue"));

        // IT department avg resolution time should be 2.0 hours
        Double itAvg = report.avgResolutionTimeByDept().get("IT");
        assertNotNull(itAvg);
        assertEquals(2.0, itAvg);
    }

    @Test
    @DisplayName("Non-admin role cannot view analytics report")
    void nonAdminThrows() {
        assertThrows(AuthorizationException.class, () -> analyticsService.getReport(studentSession));
    }

    @Test
    @DisplayName("invalidateCache clears cached metrics report")
    void invalidateCache() {
        AnalyticsReportDto r1 = analyticsService.getReport(adminSession);
        analyticsService.invalidateCache();

        // Add another ticket
        ticketRepo.save(new Ticket.Builder().title("T3").description("D").priority(Priority.LOW).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(LocalDateTime.now()).build());

        AnalyticsReportDto r2 = analyticsService.getReport(adminSession);
        assertEquals(2, r2.ticketsByStatus().get("OPEN"), "Report after cache invalidation must reflect new ticket");
    }

    @Test
    @DisplayName("Concurrent reads under ReentrantReadWriteLock return consistent data without error")
    void concurrentReadLocking() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        List<AnalyticsReportDto> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results.add(analyticsService.getReport(adminSession));
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threads, results.size());
        for (AnalyticsReportDto r : results) {
            assertEquals(1, r.ticketsByStatus().get("OPEN"));
            assertEquals(1, r.ticketsByStatus().get("RESOLVED"));
        }
    }
}
