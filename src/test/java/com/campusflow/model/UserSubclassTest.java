package com.campusflow.model;

import com.campusflow.model.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link User} and its subclasses {@link Student},
 * {@link StaffMember}, and {@link Admin}.
 *
 * SCD concepts: SCD-09 (Inheritance), SCD-11 (encapsulation, immutability).
 */
@DisplayName("User hierarchy")
class UserSubclassTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2024, 1, 1, 9, 0);
    private static final String HASH = "$2a$12$samplehashfortestingpurposes1234";

    // ── Role assignment ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Student has STUDENT role")
    void studentHasCorrectRole() {
        Student s = new Student(1, "alice", HASH, "Alice Smith", "alice@uni.edu", true, NOW);
        assertEquals(Role.STUDENT, s.getRole());
    }

    @Test
    @DisplayName("StaffMember has STAFF role")
    void staffHasCorrectRole() {
        StaffMember sm = new StaffMember(2, "bob", HASH, "Bob Jones", "bob@uni.edu", true, NOW, 1);
        assertEquals(Role.STAFF, sm.getRole());
    }

    @Test
    @DisplayName("Admin has ADMIN role")
    void adminHasCorrectRole() {
        Admin a = new Admin(3, "carol", HASH, "Carol White", "carol@uni.edu", true, NOW);
        assertEquals(Role.ADMIN, a.getRole());
    }

    // ── StaffMember additional field ─────────────────────────────────────────

    @Test
    @DisplayName("StaffMember stores departmentId correctly")
    void staffMemberDepartmentId() {
        StaffMember sm = new StaffMember(2, "bob", HASH, "Bob Jones", "bob@uni.edu", true, NOW, 3);
        assertEquals(3, sm.getDepartmentId());
    }

    @Test
    @DisplayName("StaffMember rejects departmentId <= 0")
    void staffMemberRejectsInvalidDeptId() {
        assertThrows(IllegalArgumentException.class, () ->
            new StaffMember(2, "bob", HASH, "Bob Jones", "bob@uni.edu", true, NOW, 0));
    }

    // ── equals / hashCode ────────────────────────────────────────────────────

    @Test
    @DisplayName("Two users with same id are equal (regardless of class)")
    void equalsById() {
        User s = new Student(10, "alice", HASH, "Alice", "a@b.com", true, NOW);
        User a = new Admin(10,   "admin", HASH, "Admin", "x@y.com", true, NOW);
        assertEquals(s, a); // same id → equal
    }

    @Test
    @DisplayName("hashCode consistent with equals for same id")
    void hashCodeConsistentWithEquals() {
        User s1 = new Student(5, "u1", HASH, "User One", "u1@uni.edu", true, NOW);
        User s2 = new Student(5, "u2", HASH, "User Two", "u2@uni.edu", false, NOW);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    @DisplayName("Users with different ids are not equal")
    void differentIdsNotEqual() {
        User s1 = new Student(1, "alice", HASH, "Alice", "a@uni.edu", true, NOW);
        User s2 = new Student(2, "alice", HASH, "Alice", "a@uni.edu", true, NOW);
        assertNotEquals(s1, s2);
    }

    // ── Immutability and security ────────────────────────────────────────────

    @Test
    @DisplayName("isActive is mutable and defaults to true")
    void isActiveMutable() {
        Student s = new Student(1, "alice", HASH, "Alice", "a@uni.edu", true, NOW);
        assertTrue(s.isActive());
        s.setActive(false);
        assertFalse(s.isActive());
    }

    @Test
    @DisplayName("toString does not expose passwordHash")
    void toStringDoesNotExposePasswordHash() {
        Student s = new Student(1, "alice", HASH, "Alice Smith", "a@uni.edu", true, NOW);
        assertFalse(s.toString().contains(HASH),
            "toString must not include the BCrypt hash");
    }

    @Test
    @DisplayName("User is abstract — cannot be instantiated directly")
    void userIsAbstract() {
        assertTrue(java.lang.reflect.Modifier.isAbstract(User.class.getModifiers()),
            "User class must be declared abstract");
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Null username is rejected")
    void nullUsernameRejected() {
        assertThrows(NullPointerException.class, () ->
            new Student(1, null, HASH, "Alice", "a@uni.edu", true, NOW));
    }

    @Test
    @DisplayName("Blank fullName is rejected")
    void blankFullNameRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new Student(1, "alice", HASH, "   ", "a@uni.edu", true, NOW));
    }
}
