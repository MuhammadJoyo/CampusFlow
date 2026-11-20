package com.campusflow.service;

import com.campusflow.model.Category;
import com.campusflow.model.Department;
import com.campusflow.model.Ticket;
import com.campusflow.model.enums.Priority;
import com.campusflow.model.enums.TicketStatus;
import com.campusflow.repository.CategoryRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.service.exception.ResourceNotFoundException;
import com.campusflow.service.exception.ValidationException;
import com.campusflow.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RoutingService")
class RoutingServiceTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_routing_test?mode=memory&cache=shared";

    private RoutingService routingService;
    private CategoryRepository categoryRepo;
    private DepartmentRepository deptRepo;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();

        categoryRepo = new CategoryRepository();
        deptRepo = new DepartmentRepository();
        routingService = new RoutingService(categoryRepo, deptRepo);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("Routes ticket with category 1 (Network Issue) to IT department")
    void routeToIT() {
        Ticket ticket = new Ticket.Builder()
                .title("Wi-Fi down")
                .description("Cannot connect in library")
                .priority(Priority.HIGH)
                .status(TicketStatus.OPEN)
                .categoryId(1) // Network Issue -> IT (dept 1)
                .submitterId(1)
                .createdAt(LocalDateTime.now())
                .build();

        Department dept = routingService.routeTicket(ticket);
        assertEquals(1, dept.getId());
        assertEquals("IT", dept.getName());
    }

    @Test
    @DisplayName("Routes category 4 (Room Maintenance) to Maintenance department")
    void routeToMaintenance() {
        Department dept = routingService.getDepartmentForCategory(4);
        assertEquals(2, dept.getId());
        assertEquals("Maintenance", dept.getName());
    }

    @Test
    @DisplayName("Routes category 7 (Exam Query) to Academic department")
    void routeToAcademic() {
        Department dept = routingService.getDepartmentForCategory(7);
        assertEquals(3, dept.getId());
        assertEquals("Academic", dept.getName());
    }

    @Test
    @DisplayName("Throws ValidationException when category is inactive")
    void inactiveCategoryThrows() {
        // Deactivate category 1
        categoryRepo.setActive(1, false);

        assertThrows(ValidationException.class, () -> routingService.getDepartmentForCategory(1));
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when category does not exist")
    void nonExistentCategoryThrows() {
        assertThrows(ResourceNotFoundException.class, () -> routingService.getDepartmentForCategory(9999));
    }
}
