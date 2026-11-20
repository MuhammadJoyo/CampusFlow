package com.campusflow.repository;

import com.campusflow.model.Admin;
import com.campusflow.model.StaffMember;
import com.campusflow.model.Student;
import com.campusflow.model.User;
import com.campusflow.model.enums.Role;
import com.campusflow.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repository for managing {@link User} accounts against the {@code users} table.
 *
 * <p>Polymorphically instantiates {@link Student}, {@link StaffMember}, or {@link Admin}
 * based on the persisted {@code role} column (SCD-09 OOP Inheritance).</p>
 */
public class UserRepository {

    public UserRepository() {
    }

    // ── Standalone API ────────────────────────────────────────────────────────

    public Optional<User> findById(int id) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findById(id, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find user by id: " + id, e);
        }
    }

    public Optional<User> findByUsername(String username) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findByUsername(username, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find user by username: " + username, e);
        }
    }

    public List<User> findAll() {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load all users", e);
        }
    }

    public User save(User user) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return save(user, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save user: " + user.getUsername(), e);
        }
    }

    public void update(User user) {
        try (Connection conn = DatabaseManager.getConnection()) {
            update(user, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to update user: " + user.getUsername(), e);
        }
    }

    public void setActive(int userId, boolean active) {
        try (Connection conn = DatabaseManager.getConnection()) {
            setActive(userId, active, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to update active state for user: " + userId, e);
        }
    }

    // ── Transactional API (Injected Connection) ──────────────────────────────

    public Optional<User> findById(int id, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<User> findByUsername(String username, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public List<User> findAll(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT * FROM users ORDER BY id ASC";
        List<User> list = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public User save(User user, Connection conn) throws SQLException {
        Objects.requireNonNull(user, "User must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        String sql = """
            INSERT INTO users (username, password_hash, role, full_name, email, department_id, is_active, created_at, must_change_password)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getRole().name());
            ps.setString(4, user.getFullName());
            ps.setString(5, user.getEmail());

            if (user instanceof StaffMember sm) {
                ps.setInt(6, sm.getDepartmentId());
            } else {
                ps.setNull(6, Types.INTEGER);
            }

            ps.setInt(7, user.isActive() ? 1 : 0);
            ps.setString(8, user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            ps.setInt(9, user.isMustChangePassword() ? 1 : 0);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int generatedId = keys.getInt(1);
                    return instantiateUser(generatedId, user);
                }
                throw new SQLException("Failed to obtain generated ID for user: " + user.getUsername());
            }
        }
    }

    public void update(User user, Connection conn) throws SQLException {
        Objects.requireNonNull(user, "User must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        String sql = """
            UPDATE users
            SET full_name = ?, email = ?, password_hash = ?, department_id = ?, is_active = ?, must_change_password = ?
            WHERE id = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());

            if (user instanceof StaffMember sm) {
                ps.setInt(4, sm.getDepartmentId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }

            ps.setInt(5, user.isActive() ? 1 : 0);
            ps.setInt(6, user.isMustChangePassword() ? 1 : 0);
            ps.setInt(7, user.getId());
            ps.executeUpdate();
        }
    }

    public void setActive(int userId, boolean active, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    // ── Row Mapping ──────────────────────────────────────────────────────────

    private User mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String username = rs.getString("username");
        String passwordHash = rs.getString("password_hash");
        String roleStr = rs.getString("role");
        String fullName = rs.getString("full_name");
        String email = rs.getString("email");
        int departmentId = rs.getInt("department_id");
        boolean isActive = rs.getInt("is_active") == 1;
        String createdAtStr = rs.getString("created_at");

        int mustChangeInt = 0;
        try {
            mustChangeInt = rs.getInt("must_change_password");
        } catch (SQLException ignored) {
            // legacy projection or older table info
        }
        boolean mustChangePassword = mustChangeInt == 1;

        Role role = Role.valueOf(roleStr.toUpperCase());
        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);

        return switch (role) {
            case STUDENT -> new Student(id, username, passwordHash, fullName, email, isActive, createdAt, mustChangePassword);
            case STAFF   -> new StaffMember(id, username, passwordHash, fullName, email, isActive, createdAt, departmentId, mustChangePassword);
            case ADMIN   -> new Admin(id, username, passwordHash, fullName, email, isActive, createdAt, mustChangePassword);
        };
    }

    private User instantiateUser(int id, User template) {
        return switch (template.getRole()) {
            case STUDENT -> new Student(id, template.getUsername(), template.getPasswordHash(),
                    template.getFullName(), template.getEmail(), template.isActive(), template.getCreatedAt(),
                    template.isMustChangePassword());
            case STAFF   -> new StaffMember(id, template.getUsername(), template.getPasswordHash(),
                    template.getFullName(), template.getEmail(), template.isActive(), template.getCreatedAt(),
                    ((StaffMember) template).getDepartmentId(), template.isMustChangePassword());
            case ADMIN   -> new Admin(id, template.getUsername(), template.getPasswordHash(),
                    template.getFullName(), template.getEmail(), template.isActive(), template.getCreatedAt(),
                    template.isMustChangePassword());
        };
    }
}
