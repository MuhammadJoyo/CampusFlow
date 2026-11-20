package com.campusflow.service;

import com.campusflow.server.ServerConfig;
import com.campusflow.model.User;
import com.campusflow.repository.AuditLogRepository;
import com.campusflow.repository.UserRepository;
import com.campusflow.service.dto.AuthResultDto;
import com.campusflow.service.exception.AuthenticationException;
import com.campusflow.service.exception.ValidationException;
import com.campusflow.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthService")
class AuthServiceTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:file:cf_auth_test?mode=memory&cache=shared";

    private AuthService authService;
    private UserRepository userRepo;
    private AuditLogRepository auditRepo;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseManager.init(TEST_DB_URL);
        DatabaseManager.initialiseSchema();
        DatabaseManager.seedData();

        userRepo = new UserRepository();
        auditRepo = new AuditLogRepository();
        ServerConfig config = ServerConfig.load();
        authService = new AuthService(userRepo, auditRepo, config);
    }

    @AfterEach
    void tearDown() {
        authService.shutdown();
        DatabaseManager.reset();
    }

    @Test
    @DisplayName("Successful admin login returns valid session and flags mandatory password change")
    void adminLoginSuccess() {
        AuthResultDto result = authService.authenticate("admin", "admin123");
        assertNotNull(result.sessionId());
        assertEquals(1, result.userId());
        assertEquals("admin", result.username());
        assertTrue(result.mustChangePassword(), "Seeded admin must require password change");

        Session session = authService.validateSession(result.sessionId());
        assertNotNull(session);
        assertEquals(1, session.getUserId());
        assertTrue(session.isPasswordChangeRequired());
    }

    @Test
    @DisplayName("Invalid password throws AuthenticationException")
    void invalidPasswordThrows() {
        assertThrows(AuthenticationException.class, () -> authService.authenticate("admin", "WrongPassword!"));
    }

    @Test
    @DisplayName("Non-existent username throws AuthenticationException")
    void nonExistentUsernameThrows() {
        assertThrows(AuthenticationException.class, () -> authService.authenticate("nobody", "Password123!"));
    }

    @Test
    @DisplayName("Deactivated user cannot log in")
    void deactivatedUserCannotLogin() {
        userRepo.setActive(1, false);
        assertThrows(AuthenticationException.class, () -> authService.authenticate("admin", "admin123"));
    }

    @Test
    @DisplayName("changePassword updates database hash and clears mustChangePassword persistently across sessions")
    void changePasswordPersistent() {
        AuthResultDto loginResult = authService.authenticate("admin", "admin123");
        Session session = authService.validateSession(loginResult.sessionId());

        // Change password
        authService.changePassword(session, "admin123", "NewSecretAdmin2026!");
        assertFalse(session.isPasswordChangeRequired());

        // Verify in database
        Optional<User> updatedUser = userRepo.findById(1);
        assertTrue(updatedUser.isPresent());
        assertFalse(updatedUser.get().isMustChangePassword(), "DB must have must_change_password = 0");

        // Simulate new login session from another client / after restart
        AuthResultDto newLogin = authService.authenticate("admin", "NewSecretAdmin2026!");
        assertFalse(newLogin.mustChangePassword(), "New session must reflect password change completed");

        // Old password must now fail
        assertThrows(AuthenticationException.class, () -> authService.authenticate("admin", "admin123"));
    }

    @Test
    @DisplayName("changePassword with incorrect old password throws ValidationException")
    void changePasswordWrongOldPasswordThrows() {
        AuthResultDto loginResult = authService.authenticate("admin", "admin123");
        Session session = authService.validateSession(loginResult.sessionId());

        assertThrows(ValidationException.class, () -> authService.changePassword(session, "WrongOld!", "NewPass123!"));
    }

    @Test
    @DisplayName("changePassword with password < 6 characters throws ValidationException")
    void changePasswordTooShortThrows() {
        AuthResultDto loginResult = authService.authenticate("admin", "admin123");
        Session session = authService.validateSession(loginResult.sessionId());

        assertThrows(ValidationException.class, () -> authService.changePassword(session, "admin123", "123"));
    }

    @Test
    @DisplayName("logout removes active session")
    void logoutRemovesSession() {
        AuthResultDto result = authService.authenticate("admin", "admin123");
        assertEquals(1, authService.getActiveSessionCount());

        authService.logout(result.sessionId());
        assertEquals(0, authService.getActiveSessionCount());
        assertThrows(AuthenticationException.class, () -> authService.validateSession(result.sessionId()));
    }

    @Test
    @DisplayName("invalidateUserSessions terminates all sessions for specified user ID")
    void invalidateUserSessions() {
        AuthResultDto s1 = authService.authenticate("admin", "admin123");
        AuthResultDto s2 = authService.authenticate("admin", "admin123");
        assertEquals(2, authService.getActiveSessionCount());

        authService.invalidateUserSessions(1);
        assertEquals(0, authService.getActiveSessionCount());
    }
}
