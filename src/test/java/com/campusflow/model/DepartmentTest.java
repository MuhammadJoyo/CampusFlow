package com.campusflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Department}.
 */
@DisplayName("Department entity")
class DepartmentTest {

    @Test
    @DisplayName("Department is created with correct fields")
    void departmentCreated() {
        Department d = new Department(1, "IT Support", "Handles IT issues");
        assertEquals(1, d.getId());
        assertEquals("IT Support", d.getName());
        assertEquals("Handles IT issues", d.getDescription());
    }

    @Test
    @DisplayName("Two departments with same id are equal")
    void equalsById() {
        Department d1 = new Department(3, "IT Support",  "Desc A");
        Department d2 = new Department(3, "Maintenance", "Desc B");
        assertEquals(d1, d2);
    }

    @Test
    @DisplayName("Two departments with different ids are not equal")
    void notEqualDifferentIds() {
        Department d1 = new Department(1, "IT", "");
        Department d2 = new Department(2, "IT", "");
        assertNotEquals(d1, d2);
    }

    @Test
    @DisplayName("Null name is rejected")
    void nullNameRejected() {
        assertThrows(NullPointerException.class, () ->
            new Department(1, null, "desc"));
    }

    @Test
    @DisplayName("Blank name is rejected")
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new Department(1, "  ", "desc"));
    }
}
