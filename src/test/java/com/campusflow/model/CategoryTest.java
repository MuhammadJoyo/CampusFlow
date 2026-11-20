package com.campusflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Category}.
 */
@DisplayName("Category entity")
class CategoryTest {

    @Test
    @DisplayName("Category is created with correct fields")
    void categoryCreated() {
        Category c = new Category(1, "Network Issues", "LAN/WiFi problems", 1, true);
        assertEquals(1,  c.getId());
        assertEquals("Network Issues", c.getName());
        assertEquals(1,  c.getDepartmentId());
        assertTrue(c.isActive());
    }

    @Test
    @DisplayName("Category equals by id")
    void equalsById() {
        Category c1 = new Category(5, "Name A", "Desc", 1, true);
        Category c2 = new Category(5, "Name B", "Desc", 2, false);
        assertEquals(c1, c2);
    }

    @Test
    @DisplayName("departmentId <= 0 is rejected")
    void invalidDepartmentIdRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new Category(1, "Cat", "Desc", 0, true));
    }

    @Test
    @DisplayName("Blank name is rejected")
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new Category(1, "", "Desc", 1, true));
    }
}
