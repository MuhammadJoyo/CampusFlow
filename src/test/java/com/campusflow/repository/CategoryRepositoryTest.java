package com.campusflow.repository;

import com.campusflow.model.Category;
import com.campusflow.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CategoryRepository")
class CategoryRepositoryTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_cat_repo_test?mode=memory&cache=shared";
    private CategoryRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();
        repo = new CategoryRepository();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("findById retrieves seeded Network Issue category")
    void findById() {
        Optional<Category> cat = repo.findById(1);
        assertTrue(cat.isPresent());
        assertEquals("Network Issue", cat.get().getName());
        assertEquals(1, cat.get().getDepartmentId());
        assertTrue(cat.get().isActive());
    }

    @Test
    @DisplayName("findAllActive returns all 9 seeded categories")
    void findAllActive() {
        List<Category> active = repo.findAllActive();
        assertEquals(9, active.size());
    }

    @Test
    @DisplayName("findByDepartment returns categories belonging to IT department (1)")
    void findByDepartment() {
        List<Category> itCategories = repo.findByDepartment(1);
        assertEquals(3, itCategories.size());
        for (Category c : itCategories) {
            assertEquals(1, c.getDepartmentId());
        }
    }

    @Test
    @DisplayName("save persists a new category and returns instance with generated ID")
    void saveCategory() {
        Category newCat = new Category(0, "AV Equipment", "Projectors and screens", 1, true);
        Category saved = repo.save(newCat);

        assertTrue(saved.getId() > 9);
        assertEquals("AV Equipment", saved.getName());

        Optional<Category> retrieved = repo.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("AV Equipment", retrieved.get().getName());
    }

    @Test
    @DisplayName("save with non-existent departmentId fails foreign key check")
    void invalidDepartmentForeignKeyThrows() {
        Category invalidCat = new Category(0, "Orphan Category", "Desc", 9999, true);
        assertThrows(RepositoryException.class, () -> repo.save(invalidCat));
    }

    @Test
    @DisplayName("setActive(false) deactivates category and excludes it from findAllActive")
    void setActiveDeactivates() {
        repo.setActive(1, false);

        Optional<Category> cat = repo.findById(1);
        assertTrue(cat.isPresent());
        assertFalse(cat.get().isActive());

        List<Category> active = repo.findAllActive();
        assertEquals(8, active.size());

        List<Category> all = repo.findAll();
        assertEquals(9, all.size());
    }
}
