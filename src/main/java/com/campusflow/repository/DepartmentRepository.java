package com.campusflow.repository;

import com.campusflow.model.Department;
import com.campusflow.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repository for managing {@link Department} persistence against the {@code departments} table.
 *
 * <p>Supports both standalone execution (scoped connection via {@code try-with-resources})
 * and transactional execution (caller-injected {@code Connection}).</p>
 */
public class DepartmentRepository {

    public DepartmentRepository() {
    }

    // ── Standalone API ────────────────────────────────────────────────────────

    public Optional<Department> findById(int id) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findById(id, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find department by id: " + id, e);
        }
    }

    public Optional<Department> findByName(String name) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findByName(name, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find department by name: " + name, e);
        }
    }

    public List<Department> findAll() {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load all departments", e);
        }
    }

    public Department save(Department department) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return save(department, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save department: " + department.getName(), e);
        }
    }

    // ── Transactional API (Injected Connection) ──────────────────────────────

    public Optional<Department> findById(int id, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT id, name, description FROM departments WHERE id = ?";
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

    public Optional<Department> findByName(String name, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, description FROM departments WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public List<Department> findAll(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT id, name, description FROM departments ORDER BY id ASC";
        List<Department> list = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Department save(Department department, Connection conn) throws SQLException {
        Objects.requireNonNull(department, "Department must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        String sql = "INSERT INTO departments (name, description) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, department.getName());
            ps.setString(2, department.getDescription());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int generatedId = keys.getInt(1);
                    return new Department(generatedId, department.getName(), department.getDescription());
                }
                throw new SQLException("Failed to obtain generated ID for department: " + department.getName());
            }
        }
    }

    private Department mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        return new Department(id, name, description != null ? description : "");
    }
}
