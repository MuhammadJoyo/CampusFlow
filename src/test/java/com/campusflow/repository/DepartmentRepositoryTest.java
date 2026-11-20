package com.campusflow.repository;

import com.campusflow.model.Department;
import com.campusflow.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DepartmentRepository")
class DepartmentRepositoryTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_dept_repo_test?mode=memory&cache=shared";
    private DepartmentRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();
        repo = new DepartmentRepository();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("findById retrieves seeded IT department")
    void findById() {
        Optional<Department> dept = repo.findById(1);
        assertTrue(dept.isPresent());
        assertEquals("IT", dept.get().getName());
        assertTrue(dept.get().getDescription().contains("Information Technology"));
    }

    @Test
    @DisplayName("findByName retrieves Maintenance department")
    void findByName() {
        Optional<Department> dept = repo.findByName("Maintenance");
        assertTrue(dept.isPresent());
        assertEquals(2, dept.get().getId());
    }

    @Test
    @DisplayName("findByName with non-existent name returns empty")
    void findByNameNotFound() {
        Optional<Department> dept = repo.findByName("NonExistentDept");
        assertTrue(dept.isEmpty());
    }

    @Test
    @DisplayName("findAll returns all seeded departments in ID order")
    void findAll() {
        List<Department> list = repo.findAll();
        assertEquals(3, list.size());
        assertEquals("IT", list.get(0).getName());
        assertEquals("Maintenance", list.get(1).getName());
        assertEquals("Academic", list.get(2).getName());
    }

    @Test
    @DisplayName("save persists new department and returns instance with generated ID")
    void saveDepartment() {
        Department newDept = new Department(0, "Security", "Campus Safety and Security");
        Department saved = repo.save(newDept);

        assertTrue(saved.getId() > 3);
        assertEquals("Security", saved.getName());

        Optional<Department> retrieved = repo.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("Security", retrieved.get().getName());
    }

    @Test
    @DisplayName("save duplicate department name throws RepositoryException")
    void duplicateNameThrows() {
        Department duplicate = new Department(0, "IT", "Another IT");
        assertThrows(RepositoryException.class, () -> repo.save(duplicate));
    }
}
