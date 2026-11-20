package com.campusflow.service;

import com.campusflow.server.ServerConfig;
import com.campusflow.model.StaffMember;
import com.campusflow.model.User;
import com.campusflow.repository.AuditLogRepository;
import com.campusflow.repository.UserRepository;
import com.campusflow.service.dto.AuthResultDto;
import com.campusflow.service.exception.AuthenticationException;
import com.campusflow.service.exception.ValidationException;
import com.campusflow.util.DatabaseManager;
import com.campusflow.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service managing user authentication, BCrypt verification, session lifecycle,
 * and persistent password change enforcement (FR-02, ADR-003, ADR-004, NFR-07).
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepo;
    private final AuditLogRepository auditRepo;
    private final int sessionTimeoutMinutes;
    private final ConcurrentMap<String, Session> sessions;
    private final ScheduledExecutorService sessionCleaner;

    public AuthService(UserRepository userRepo, AuditLogRepository auditRepo, ServerConfig config) {
        this.userRepo = Objects.requireNonNull(userRepo, "userRepo must not be null");
        this.auditRepo = Objects.requireNonNull(auditRepo, "auditRepo must not be null");
        Objects.requireNonNull(config, "config must not be null");

        this.sessionTimeoutMinutes = config.getSessionTimeoutMinutes();
        this.sessions = new ConcurrentHashMap<>();

        // Daemon ScheduledExecutorService running every 5 minutes to sweep inactive sessions (ADR-003)
        this.sessionCleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "campusflow-session-cleaner");
            t.setDaemon(true);
            return t;
        });
        this.sessionCleaner.scheduleAtFixedRate(this::cleanExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Authenticates a user with username and password.
     *
     * @param username user login name
     * @param password user plaintext password
     * @return sanitized AuthResultDto containing session token and profile info
     * @throws AuthenticationException if credentials fail or account is inactive
     */
    public AuthResultDto authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new AuthenticationException("Username and password must not be blank.");
        }

        User user = userRepo.findByUsername(username.trim())
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));

        if (!user.isActive()) {
            throw new AuthenticationException("Account is deactivated. Contact an administrator.");
        }

        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }

        // Persistent check: either persistent database flag is 1, or default admin password matches seed
        boolean mustChange = user.isMustChangePassword();
        if (user.getUsername().equals("admin") && PasswordUtil.verify("admin123", user.getPasswordHash())) {
            mustChange = true;
        }

        Integer deptId = (user instanceof StaffMember sm) ? sm.getDepartmentId() : null;
        String sessionId = UUID.randomUUID().toString();

        Session session = new Session(sessionId, user.getId(), user.getUsername(), user.getRole(), deptId, mustChange);
        sessions.put(sessionId, session);

        auditRepo.log(user.getId(), "LOGIN", "USER", user.getId(), "User logged in successfully");
        log.info("User '{}' (ID: {}) authenticated successfully. Session: {}", user.getUsername(), user.getId(), sessionId);

        return new AuthResultDto(sessionId, user.getId(), user.getUsername(), user.getRole(), user.getFullName(), deptId, mustChange);
    }

    /**
     * Validates a session token and updates its last accessed timestamp.
     *
     * @param sessionId the session UUID
     * @return validated Session object
     * @throws AuthenticationException if the session is invalid, expired, or missing
     */
    public Session validateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new AuthenticationException("Missing or empty session token.");
        }

        Session session = sessions.get(sessionId.trim());
        if (session == null) {
            throw new AuthenticationException("Session is invalid or has expired.");
        }

        if (session.isExpired(sessionTimeoutMinutes)) {
            sessions.remove(sessionId.trim());
            log.info("Session '{}' expired due to inactivity (> {} min).", sessionId, sessionTimeoutMinutes);
            throw new AuthenticationException("Session has expired due to inactivity. Please log in again.");
        }

        session.touch();
        return session;
    }

    /**
     * Changes a user's password and clears the mandatory password change flag.
     *
     * @param session     authenticated session of the requesting user
     * @param oldPassword current password
     * @param newPassword new password
     * @throws ValidationException if the old password is wrong or new password is invalid
     */
    public void changePassword(Session session, String oldPassword, String newPassword) {
        Objects.requireNonNull(session, "session must not be null");

        if (oldPassword == null || oldPassword.isBlank()) {
            throw new ValidationException("Current password must not be blank.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new ValidationException("New password must be at least 6 characters in length.");
        }

        User user = userRepo.findById(session.getUserId())
                .orElseThrow(() -> new ValidationException("User account not found for ID: " + session.getUserId()));

        if (!PasswordUtil.verify(oldPassword, user.getPasswordHash())) {
            throw new ValidationException("Current password does not match.");
        }

        String newHash = PasswordUtil.hash(newPassword);
        user.setMustChangePassword(false);

        // Transactional update across user and audit log
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Update user in DB with new hash and must_change_password = 0
                User updatedTemplate = instantiateWithNewPassword(user, newHash, false);
                userRepo.update(updatedTemplate, conn);
                auditRepo.log(session.getUserId(), "CHANGE_PASSWORD", "USER", session.getUserId(), "Password changed successfully", conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new ValidationException("Failed to update password in database: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new ValidationException("Database connection error: " + e.getMessage());
        }

        session.setPasswordChangeRequired(false);
        log.info("User '{}' (ID: {}) successfully updated password.", user.getUsername(), user.getId());
    }

    /**
     * Logs out an active session.
     *
     * @param sessionId the session UUID to remove
     */
    public void logout(String sessionId) {
        if (sessionId != null) {
            Session removed = sessions.remove(sessionId.trim());
            if (removed != null) {
                auditRepo.log(removed.getUserId(), "LOGOUT", "USER", removed.getUserId(), "User logged out");
                log.info("Session '{}' for user '{}' logged out.", sessionId, removed.getUsername());
            }
        }
    }

    /**
     * Terminates all active sessions belonging to the given user ID (e.g. upon account deactivation).
     *
     * @param userId user database ID
     */
    public void invalidateUserSessions(int userId) {
        sessions.values().removeIf(s -> s.getUserId() == userId);
        log.info("All active sessions invalidated for user ID: {}", userId);
    }

    /**
     * Sweeps and removes all inactive sessions exceeding the configured timeout.
     */
    public void cleanExpiredSessions() {
        int before = sessions.size();
        sessions.values().removeIf(s -> s.isExpired(sessionTimeoutMinutes));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("Cleaned {} expired sessions. Active count: {}", removed, sessions.size());
        }
    }

    /**
     * Returns the current number of active in-memory sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Gracefully shuts down the background session cleaner executor.
     */
    public void shutdown() {
        sessionCleaner.shutdown();
        try {
            if (!sessionCleaner.awaitTermination(2, TimeUnit.SECONDS)) {
                sessionCleaner.shutdownNow();
            }
        } catch (InterruptedException e) {
            sessionCleaner.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private User instantiateWithNewPassword(User user, String newHash, boolean mustChangePassword) {
        return switch (user.getRole()) {
            case STUDENT -> new com.campusflow.model.Student(
                    user.getId(), user.getUsername(), newHash, user.getFullName(), user.getEmail(), user.isActive(), user.getCreatedAt(), mustChangePassword);
            case STAFF   -> new com.campusflow.model.StaffMember(
                    user.getId(), user.getUsername(), newHash, user.getFullName(), user.getEmail(), user.isActive(), user.getCreatedAt(), ((StaffMember) user).getDepartmentId(), mustChangePassword);
            case ADMIN   -> new com.campusflow.model.Admin(
                    user.getId(), user.getUsername(), newHash, user.getFullName(), user.getEmail(), user.isActive(), user.getCreatedAt(), mustChangePassword);
        };
    }
}
