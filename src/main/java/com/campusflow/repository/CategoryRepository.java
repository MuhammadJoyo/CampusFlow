package com.campusflow.repository;

import com.campusflow.model.Category;
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
 * Repository for managing {@link Category} persistence against the {@code categories} table.
 */
public class CategoryRepository {

    public CategoryRepository() {
    }

    // ── Standalone API ────────────────────────────────────────────────────────

    public Optional<Category> findById(int id) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findById(id, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find category by id: " + id, e);
        }
    }

    public List<Category> findAllActive() {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findAllActive(conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load active categories", e);
        }
    }

    public List<Category> findAll() {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load all categories", e);
        }
    }

    public List<Category> findByDepartment(int departmentId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findByDepartment(departmentId, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load categories for department: " + departmentId, e);
        }
    }

    public Category save(Category category) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return save(category, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save category: " + category.getName(), e);
        }
    }

    public void setActive(int categoryId, boolean active) {
        try (Connection conn = DatabaseManager.getConnection()) {
            setActive(categoryId, active, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to update active status for category: " + categoryId, e);
        }
    }

    // ── Transactional API (Injected Connection) ──────────────────────────────

    public Optional<Category> findById(int id, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT id, name, description, department_id, is_active FROM categories WHERE id = ?";
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

    public List<Category> findAllActive(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT id, name, description, department_id, is_active FROM categories WHERE is_active = 1 ORDER BY name ASC";
        List<Category> list = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Category> findAll(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT id, name, description, department_id, is_active FROM categories ORDER BY name ASC";
        List<Category> list = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Category> findByDepartment(int departmentId, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT id, name, description, department_id, is_active FROM categories WHERE department_id = ? AND is_active = 1 ORDER BY name ASC";
        List<Category> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public Category save(Category category, Connection conn) throws SQLException {
        Objects.requireNonNull(category, "Category must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        String sql = "INSERT INTO categories (name, description, department_id, is_active) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.setInt(3, category.getDepartmentId());
            ps.setInt(4, category.isActive() ? 1 : 0);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int generatedId = keys.getInt(1);
                    return new Category(generatedId, category.getName(), category.getDescription(), category.getDepartmentId(), category.isActive());
                }
                throw new SQLException("Failed to obtain generated ID for category: " + category.getName());
            }
        }
    }

    public void setActive(int categoryId, boolean active, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "UPDATE categories SET is_active = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, categoryId);
            ps.executeUpdate();
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        int departmentId = rs.getInt("department_id");
        boolean isActive = rs.getInt("is_active") == 1;

        return new Category(id, name, description != null ? description : "", departmentId, isActive);
    }
}
