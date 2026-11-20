package com.campusflow.service;

import com.campusflow.model.Admin;
import com.campusflow.model.Department;
import com.campusflow.model.StaffMember;
import com.campusflow.model.Student;
import com.campusflow.model.User;
import com.campusflow.model.enums.Role;
import com.campusflow.repository.AuditLogRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.UserRepository;
import com.campusflow.service.dto.UserCreateDto;
import com.campusflow.service.exception.AuthorizationException;
import com.campusflow.service.exception.ResourceNotFoundException;
import com.campusflow.service.exception.ValidationException;
import com.campusflow.util.DatabaseManager;
import com.campusflow.util.PasswordUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for administrative user provisioning, deactivation, and inquiry (FR-01, UC-09, UC-10).
 */
public class UserManagementService {

    private final UserRepository userRepo;
    private final DepartmentRepository deptRepo;
    private final AuthService authService;
    private final AuditLogRepository auditRepo;

    private static final Object ADMIN_DEACTIVATION_LOCK = new Object();

    public UserManagementService(UserRepository userRepo,
                                 DepartmentRepository deptRepo,
                                 AuthService authService,
                                 AuditLogRepository auditRepo) {
        this.userRepo = Objects.requireNonNull(userRepo, "userRepo must not be null");
        this.deptRepo = Objects.requireNonNull(deptRepo, "deptRepo must not be null");
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.auditRepo = Objects.requireNonNull(auditRepo, "auditRepo must not be null");
    }

    /**
     * Creates a new user account with hashed password.
     *
     * @param adminSession authenticated administrator session
     * @param dto          user creation parameters
     * @return persisted User instance (password hash is sanitized in API representations)
     */
    public User createUser(Session adminSession, UserCreateDto dto) {
        Objects.requireNonNull(adminSession, "adminSession must not be null");
        adminSession.requireRole(Role.ADMIN);
        Objects.requireNonNull(dto, "dto must not be null");

        if (dto.password().length() < 6) {
            throw new ValidationException("Password must be at least 6 characters in length.");
        }
        if (!dto.email().contains("@")) {
            throw new ValidationException("Invalid email format: " + dto.email());
        }

        // Check username uniqueness
        if (userRepo.findByUsername(dto.username()).isPresent()) {
            throw new ValidationException("Username '" + dto.username() + "' is already taken.");
        }

        String passwordHash = PasswordUtil.hash(dto.password());
        LocalDateTime now = LocalDateTime.now();
        User user;

        if (dto.role() == Role.STAFF) {
            if (dto.departmentId() == null || dto.departmentId() <= 0) {
                throw new ValidationException("Staff members must be assigned to a valid department.");
            }
            Department dept = deptRepo.findById(dto.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + dto.departmentId()));
            user = new StaffMember(0, dto.username(), passwordHash, dto.fullName(), dto.email(), true, now, dept.getId());
        } else if (dto.role() == Role.STUDENT) {
            user = new Student(0, dto.username(), passwordHash, dto.fullName(), dto.email(), true, now);
        } else if (dto.role() == Role.ADMIN) {
            user = new Admin(0, dto.username(), passwordHash, dto.fullName(), dto.email(), true, now);
        } else {
            throw new ValidationException("Unsupported user role: " + dto.role());
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                User saved = userRepo.save(user, conn);
                auditRepo.log(adminSession.getUserId(), "CREATE", "USER", saved.getId(),
                        "Created " + saved.getRole() + " account for '" + saved.getUsername() + "'", conn);
                conn.commit();
                return saved;
            } catch (SQLException e) {
                conn.rollback();
                throw new ValidationException("Failed to persist user: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new ValidationException("Database connection error: " + e.getMessage());
        }
    }

    /**
     * Deactivates a user account and terminates all their active sessions.
     * Enforces the invariant that the last active administrator cannot be deactivated.
     *
     * @param adminSession authenticated administrator session
     * @param targetUserId user ID to deactivate
     */
    public void deactivateUser(Session adminSession, int targetUserId) {
        Objects.requireNonNull(adminSession, "adminSession must not be null");
        adminSession.requireRole(Role.ADMIN);

        User targetUser = userRepo.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + targetUserId));

        if (!targetUser.isActive()) {
            return; // Already deactivated
        }

        synchronized (ADMIN_DEACTIVATION_LOCK) {
            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Atomic last-active-admin check inside write transaction
                    if (targetUser.getRole() == Role.ADMIN) {
                        String countSql = "SELECT COUNT(*) FROM users WHERE UPPER(role) = 'ADMIN' AND is_active = 1";
                        try (Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery(countSql)) {
                            if (rs.next() && rs.getInt(1) <= 1) {
                                throw new ValidationException("Cannot deactivate the last remaining active administrator.");
                            }
                        }
                    }

                    userRepo.setActive(targetUserId, false, conn);
                    auditRepo.log(adminSession.getUserId(), "DEACTIVATE", "USER", targetUserId,
                            "Deactivated " + targetUser.getRole() + " account for '" + targetUser.getUsername() + "'", conn);
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw new ValidationException("Failed to deactivate user: " + e.getMessage());
                }
            } catch (SQLException e) {
                throw new ValidationException("Database connection error: " + e.getMessage());
            }
        }

        // Post-commit: terminate any active sessions
        authService.invalidateUserSessions(targetUserId);
    }

    /**
     * Retrieves all users (requires ADMIN role).
     */
    public List<User> getAllUsers(Session adminSession) {
        Objects.requireNonNull(adminSession, "adminSession must not be null");
        adminSession.requireRole(Role.ADMIN);
        return userRepo.findAll();
    }

    /**
     * Retrieves a user by ID with authorization check (Admin or self).
     */
    public Optional<User> getUserById(Session session, int userId) {
        Objects.requireNonNull(session, "session must not be null");
        session.requireNotRequiringPasswordChange();

        if (session.getRole() != Role.ADMIN && session.getUserId() != userId) {
            throw new AuthorizationException("Cannot access profile data of another user.");
        }
        return userRepo.findById(userId);
    }
}
