package com.campusflow.service;

import com.campusflow.server.ServerConfig;
import com.campusflow.model.Admin;
import com.campusflow.model.StaffMember;
import com.campusflow.model.Student;
import com.campusflow.model.User;
import com.campusflow.model.enums.Role;
import com.campusflow.repository.AuditLogRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.UserRepository;
import com.campusflow.service.dto.UserCreateDto;
import com.campusflow.service.exception.AuthorizationException;
import com.campusflow.service.exception.ValidationException;
import com.campusflow.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserManagementService")
class UserManagementServiceTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_user_mgmt_test?mode=memory&cache=shared";

    private UserManagementService userMgmtService;
    private AuthService authService;
    private UserRepository userRepo;
    private DepartmentRepository deptRepo;
    private AuditLogRepository auditRepo;

    private Session adminSession;
    private Session studentSession;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();

        userRepo = new UserRepository();
        deptRepo = new DepartmentRepository();
        auditRepo = new AuditLogRepository();
        com.campusflow.server.ServerConfig config = com.campusflow.server.ServerConfig.load();
        authService = new AuthService(userRepo, auditRepo, config);
        userMgmtService = new UserManagementService(userRepo, deptRepo, authService, auditRepo);

        adminSession = new Session("admin-session-id", 1, "admin", Role.ADMIN, null, false);
        studentSession = new Session("student-session-id", 2, "student", Role.STUDENT, null, false);
    }

    @AfterEach
    void tearDown() {
        authService.shutdown();
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("Admin can create student and staff accounts polymorphically")
    void createUserSuccess() {
        UserCreateDto studentDto = new UserCreateDto("alice", "Password123!", Role.STUDENT, "Alice Student", "alice@test.com", null);
        User student = userMgmtService.createUser(adminSession, studentDto);
        assertInstanceOf(Student.class, student);
        assertEquals("alice", student.getUsername());

        UserCreateDto staffDto = new UserCreateDto("bob_it", "Password123!", Role.STAFF, "Bob Tech", "bob@test.com", 1);
        User staff = userMgmtService.createUser(adminSession, staffDto);
        assertInstanceOf(StaffMember.class, staff);
        assertEquals(1, ((StaffMember) staff).getDepartmentId());

        assertFalse(auditRepo.findByEntity("USER", student.getId()).isEmpty());
    }

    @Test
    @DisplayName("Non-admin role cannot create user accounts")
    void nonAdminCreateThrows() {
        UserCreateDto dto = new UserCreateDto("alice2", "Password123!", Role.STUDENT, "Alice", "a2@test.com", null);
        assertThrows(AuthorizationException.class, () -> userMgmtService.createUser(studentSession, dto));
    }

    @Test
    @DisplayName("Staff creation without valid departmentId throws ValidationException")
    void staffWithoutDepartmentThrows() {
        UserCreateDto dto = new UserCreateDto("staff_no_dept", "Password123!", Role.STAFF, "Staff", "st@test.com", null);
        assertThrows(ValidationException.class, () -> userMgmtService.createUser(adminSession, dto));
    }

    @Test
    @DisplayName("Duplicate username throws ValidationException")
    void duplicateUsernameThrows() {
        UserCreateDto dto1 = new UserCreateDto("same_user", "Password123!", Role.STUDENT, "User 1", "u1@test.com", null);
        userMgmtService.createUser(adminSession, dto1);

        UserCreateDto dto2 = new UserCreateDto("same_user", "Password123!", Role.STUDENT, "User 2", "u2@test.com", null);
        assertThrows(ValidationException.class, () -> userMgmtService.createUser(adminSession, dto2));
    }

    @Test
    @DisplayName("Deactivating user terminates their active sessions immediately")
    void deactivateUserTerminatesSessions() {
        UserCreateDto dto = new UserCreateDto("charlie", "Password123!", Role.STUDENT, "Charlie", "charlie@test.com", null);
        User charlie = userMgmtService.createUser(adminSession, dto);

        // Charlie logs in
        authService.authenticate("charlie", "Password123!");
        assertEquals(1, authService.getActiveSessionCount());

        // Admin deactivates Charlie
        userMgmtService.deactivateUser(adminSession, charlie.getId());
        assertEquals(0, authService.getActiveSessionCount(), "Active sessions for deactivated user must be terminated");
    }

    @Test
    @DisplayName("Attempting to deactivate the last remaining active administrator is rejected with ValidationException")
    void cannotDeactivateLastAdmin() {
        // admin (ID 1) is the only admin
        assertThrows(ValidationException.class, () -> userMgmtService.deactivateUser(adminSession, 1));
        assertTrue(userRepo.findById(1).orElseThrow().isActive(), "Last admin must remain active");
    }

    @Test
    @DisplayName("Concurrent deactivation requests cannot deactivate both admins when only two exist")
    void concurrentDeactivationLeavesAtLeastOneAdmin() throws InterruptedException {
        // Create second admin
        UserCreateDto secondAdminDto = new UserCreateDto("admin2", "Password123!", Role.ADMIN, "Admin Two", "admin2@test.com", null);
        User admin2 = userMgmtService.createUser(adminSession, secondAdminDto);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Thread 1 deactivates admin 1
        executor.submit(() -> {
            try {
                startLatch.await();
                userMgmtService.deactivateUser(adminSession, 1);
                successCount.incrementAndGet();
            } catch (ValidationException e) {
                failureCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2 deactivates admin 2
        executor.submit(() -> {
            try {
                startLatch.await();
                userMgmtService.deactivateUser(adminSession, admin2.getId());
                successCount.incrementAndGet();
            } catch (ValidationException e) {
                failureCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // One must succeed and one must fail!
        assertEquals(1, successCount.get(), "Exactly one deactivation should succeed");
        assertEquals(1, failureCount.get(), "The other deactivation must be rejected");

        // Verify in DB that exactly one admin is still active
        List<User> admins = userRepo.findAll().stream().filter(u -> u.getRole() == Role.ADMIN && u.isActive()).toList();
        assertEquals(1, admins.size(), "At least one administrator must remain active");
    }
}
