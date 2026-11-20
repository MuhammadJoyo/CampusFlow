package com.campusflow.service;

import com.campusflow.model.Category;
import com.campusflow.model.Department;
import com.campusflow.model.enums.Role;
import com.campusflow.repository.AuditLogRepository;
import com.campusflow.repository.CategoryRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.service.exception.AuthorizationException;
import com.campusflow.service.exception.ResourceNotFoundException;
import com.campusflow.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CategoryService")
class CategoryServiceTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_cat_service_test?mode=memory&cache=shared";

    private CategoryService categoryService;
    private CategoryRepository categoryRepo;
    private DepartmentRepository deptRepo;
    private AuditLogRepository auditRepo;

    private Session adminSession;
    private Session studentSession;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();

        categoryRepo = new CategoryRepository();
        deptRepo = new DepartmentRepository();
        auditRepo = new AuditLogRepository();
        categoryService = new CategoryService(categoryRepo, deptRepo, auditRepo);

        adminSession = new Session("admin-session-uuid", 1, "admin", Role.ADMIN, null, false);
        studentSession = new Session("student-session-uuid", 2, "student", Role.STUDENT, null, false);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("Admin can create a new category under an existing department with audit logging")
    void createCategorySuccess() {
        Category cat = categoryService.createCategory(adminSession, "Server Outage", "Server room problems", 1);
        assertTrue(cat.getId() > 0);
        assertEquals("Server Outage", cat.getName());
        assertEquals(1, cat.getDepartmentId());
        assertTrue(cat.isActive());

        // Verify in DB and audit log
        assertTrue(categoryRepo.findById(cat.getId()).isPresent());
        assertFalse(auditRepo.findByEntity("CATEGORY", cat.getId()).isEmpty());
    }

    @Test
    @DisplayName("Non-admin role cannot create categories")
    void nonAdminCreateThrows() {
        assertThrows(AuthorizationException.class, () ->
                categoryService.createCategory(studentSession, "New Cat", "Desc", 1));
    }

    @Test
    @DisplayName("Creating category under non-existent department throws ResourceNotFoundException")
    void nonExistentDepartmentThrows() {
        assertThrows(ResourceNotFoundException.class, () ->
                categoryService.createCategory(adminSession, "Cat", "Desc", 9999));
    }

    @Test
    @DisplayName("Admin can deactivate category and it is excluded from active categories list")
    void deactivateCategory() {
        categoryService.deactivateCategory(adminSession, 1);

        assertFalse(categoryRepo.findById(1).orElseThrow().isActive());
        List<Category> active = categoryService.getAllActiveCategories();
        assertEquals(8, active.size());
    }

    @Test
    @DisplayName("getAllDepartments returns seeded departments")
    void getAllDepartments() {
        List<Department> departments = categoryService.getAllDepartments();
        assertEquals(3, departments.size());
    }
}
