package com.campusflow.repository;

import com.campusflow.model.Admin;
import com.campusflow.model.StaffMember;
import com.campusflow.model.Student;
import com.campusflow.model.User;
import com.campusflow.model.enums.Role;
import com.campusflow.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserRepository")
class UserRepositoryTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_user_repo_test?mode=memory&cache=shared";
    private static final String DUMMY_HASH = "$2a$12$e8Y9X5J9H4Z3Q2W1E0R9T8Y7U6I5O4P3A2S1D0F9G8H7J6K5L4M3N";
    private UserRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();
        repo = new UserRepository();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("findByUsername retrieves seeded admin as Admin instance")
    void findByUsernameAdmin() {
        Optional<User> user = repo.findByUsername("admin");
        assertTrue(user.isPresent());
        assertInstanceOf(Admin.class, user.get());
        assertEquals("admin", user.get().getUsername());
        assertEquals(Role.ADMIN, user.get().getRole());
        assertTrue(user.get().isActive());
    }

    @Test
    @DisplayName("save and findById for Student polymorphically hydrates Student instance")
    void saveAndHydrateStudent() {
        Student student = new Student(0, "alice", DUMMY_HASH, "Alice Walker", "alice@campus.edu", true, LocalDateTime.now());
        User saved = repo.save(student);

        assertTrue(saved.getId() > 1);
        assertInstanceOf(Student.class, saved);

        Optional<User> retrieved = repo.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertInstanceOf(Student.class, retrieved.get());
        assertEquals("alice", retrieved.get().getUsername());
        assertEquals(Role.STUDENT, retrieved.get().getRole());
    }

    @Test
    @DisplayName("save and findById for StaffMember preserves departmentId")
    void saveAndHydrateStaffMember() {
        StaffMember staff = new StaffMember(0, "bob_tech", DUMMY_HASH, "Bob Miller", "bob@campus.edu", true, LocalDateTime.now(), 1);
        User saved = repo.save(staff);

        assertInstanceOf(StaffMember.class, saved);
        assertEquals(1, ((StaffMember) saved).getDepartmentId());

        Optional<User> retrieved = repo.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertInstanceOf(StaffMember.class, retrieved.get());
        assertEquals(1, ((StaffMember) retrieved.get()).getDepartmentId());
        assertEquals(Role.STAFF, retrieved.get().getRole());
    }

    @Test
    @DisplayName("findAll loads all users including different role types")
    void findAllUsers() {
        repo.save(new Student(0, "student1", DUMMY_HASH, "Student One", "s1@campus.edu", true, LocalDateTime.now()));
        repo.save(new StaffMember(0, "staff1", DUMMY_HASH, "Staff One", "st1@campus.edu", true, LocalDateTime.now(), 2));

        List<User> all = repo.findAll();
        assertEquals(3, all.size()); // 1 seeded Admin + 2 new
    }

    @Test
    @DisplayName("update modifies full_name and email")
    void updateUser() {
        Student student = (Student) repo.save(new Student(0, "charlie", DUMMY_HASH, "Charlie Initial", "charlie@campus.edu", true, LocalDateTime.now()));

        Student updatedTemplate = new Student(student.getId(), student.getUsername(), student.getPasswordHash(),
                "Charlie Updated", "charlie.new@campus.edu", student.isActive(), student.getCreatedAt());

        repo.update(updatedTemplate);

        Optional<User> retrieved = repo.findById(student.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("Charlie Updated", retrieved.get().getFullName());
        assertEquals("charlie.new@campus.edu", retrieved.get().getEmail());
    }

    @Test
    @DisplayName("setActive toggles active state")
    void setActive() {
        Student student = (Student) repo.save(new Student(0, "dave", DUMMY_HASH, "Dave", "dave@campus.edu", true, LocalDateTime.now()));
        assertTrue(student.isActive());

        repo.setActive(student.getId(), false);

        Optional<User> retrieved = repo.findById(student.getId());
        assertTrue(retrieved.isPresent());
        assertFalse(retrieved.get().isActive());
    }

    @Test
    @DisplayName("Duplicate username throws RepositoryException")
    void duplicateUsernameThrows() {
        Student s1 = new Student(0, "same_user", DUMMY_HASH, "User 1", "u1@campus.edu", true, LocalDateTime.now());
        Student s2 = new Student(0, "same_user", DUMMY_HASH, "User 2", "u2@campus.edu", true, LocalDateTime.now());

        repo.save(s1);
        assertThrows(RepositoryException.class, () -> repo.save(s2));
    }
}
