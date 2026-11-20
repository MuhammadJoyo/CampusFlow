package com.campusflow.repository;

import com.campusflow.model.Ticket;
import com.campusflow.model.enums.Priority;
import com.campusflow.model.enums.Role;
import com.campusflow.model.enums.TicketStatus;
import com.campusflow.tql.TicketFilter;
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
 * Repository for managing {@link Ticket} persistence against the {@code tickets} table.
 *
 * <p>Implements server-side authorization scoping and dynamic query generation via
 * {@link TicketFilter#buildWhereClause(Role, int)} (ADR-002, SRS FR-12).</p>
 */
public class TicketRepository {

    public TicketRepository() {
    }

    // ── Standalone API ────────────────────────────────────────────────────────

    public Optional<Ticket> findById(int id) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findById(id, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find ticket by id: " + id, e);
        }
    }

    public List<Ticket> findBySubmitter(int userId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findBySubmitter(userId, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find tickets for submitter: " + userId, e);
        }
    }

    public List<Ticket> findByAssignedStaff(int staffId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findByAssignedStaff(staffId, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find tickets for staff: " + staffId, e);
        }
    }

    public List<Ticket> findAll() {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load all tickets", e);
        }
    }

    public Ticket save(Ticket ticket) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return save(ticket, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save ticket: " + ticket.getTitle(), e);
        }
    }

    public void update(Ticket ticket) {
        try (Connection conn = DatabaseManager.getConnection()) {
            update(ticket, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to update ticket id: " + ticket.getId(), e);
        }
    }

    public List<Ticket> search(TicketFilter filter, Role role, int userId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return search(filter, role, userId, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to search tickets with filter", e);
        }
    }

    // ── Transactional API (Injected Connection) ──────────────────────────────

    public Optional<Ticket> findById(int id, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT * FROM tickets WHERE id = ?";
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

    public List<Ticket> findBySubmitter(int userId, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT * FROM tickets WHERE submitter_id = ? ORDER BY created_at DESC, id DESC";
        List<Ticket> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<Ticket> findByAssignedStaff(int staffId, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT * FROM tickets WHERE assigned_staff_id = ? ORDER BY created_at DESC, id DESC";
        List<Ticket> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<Ticket> findAll(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT * FROM tickets ORDER BY created_at DESC, id DESC";
        List<Ticket> list = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Ticket save(Ticket ticket, Connection conn) throws SQLException {
        Objects.requireNonNull(ticket, "Ticket must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        String sql = """
            INSERT INTO tickets (title, description, priority, status, category_id, submitter_id,
                                assigned_staff_id, created_at, updated_at, resolved_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ticket.getTitle());
            ps.setString(2, ticket.getDescription());
            ps.setString(3, ticket.getPriority().name());
            ps.setString(4, ticket.getStatus().name());
            ps.setInt(5, ticket.getCategoryId());
            ps.setInt(6, ticket.getSubmitterId());

            if (ticket.getAssignedStaffId() != null) {
                ps.setInt(7, ticket.getAssignedStaffId());
            } else {
                ps.setNull(7, Types.INTEGER);
            }

            ps.setString(8, ticket.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            ps.setString(9, ticket.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            if (ticket.getResolvedAt() != null) {
                ps.setString(10, ticket.getResolvedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else {
                ps.setNull(10, Types.VARCHAR);
            }

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int generatedId = keys.getInt(1);
                    return new Ticket.Builder()
                            .id(generatedId)
                            .title(ticket.getTitle())
                            .description(ticket.getDescription())
                            .priority(ticket.getPriority())
                            .status(ticket.getStatus())
                            .categoryId(ticket.getCategoryId())
                            .submitterId(ticket.getSubmitterId())
                            .assignedStaffId(ticket.getAssignedStaffId())
                            .createdAt(ticket.getCreatedAt())
                            .updatedAt(ticket.getUpdatedAt())
                            .resolvedAt(ticket.getResolvedAt())
                            .build();
                }
                throw new SQLException("Failed to obtain generated ID for ticket");
            }
        }
    }

    public void update(Ticket ticket, Connection conn) throws SQLException {
        Objects.requireNonNull(ticket, "Ticket must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        String sql = """
            UPDATE tickets
            SET title = ?, description = ?, priority = ?, status = ?,
                assigned_staff_id = ?, updated_at = ?, resolved_at = ?
            WHERE id = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticket.getTitle());
            ps.setString(2, ticket.getDescription());
            ps.setString(3, ticket.getPriority().name());
            ps.setString(4, ticket.getStatus().name());

            if (ticket.getAssignedStaffId() != null) {
                ps.setInt(5, ticket.getAssignedStaffId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }

            ps.setString(6, ticket.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            if (ticket.getResolvedAt() != null) {
                ps.setString(7, ticket.getResolvedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else {
                ps.setNull(7, Types.VARCHAR);
            }

            ps.setInt(8, ticket.getId());
            ps.executeUpdate();
        }
    }

    public List<Ticket> search(TicketFilter filter, Role role, int userId, Connection conn) throws SQLException {
        Objects.requireNonNull(filter, "TicketFilter must not be null");
        Objects.requireNonNull(role, "Role must not be null for server-side authorization");
        Objects.requireNonNull(conn, "Connection must not be null");

        TicketFilter.ParameterizedWhere where = filter.buildWhereClause(role, userId);

        String sql = """
            SELECT t.id, t.title, t.description, t.priority, t.status,
                   t.category_id, t.submitter_id, t.assigned_staff_id,
                   t.created_at, t.updated_at, t.resolved_at
            FROM tickets t
            JOIN categories c ON t.category_id = c.id
            JOIN departments d ON c.department_id = d.id
            JOIN users u_sub ON t.submitter_id = u_sub.id
            LEFT JOIN users u_assign ON t.assigned_staff_id = u_assign.id
            """ + " " + where.whereClause() + """
            ORDER BY
              CASE UPPER(t.priority)
                WHEN 'CRITICAL' THEN 0
                WHEN 'HIGH' THEN 1
                WHEN 'MEDIUM' THEN 2
                ELSE 3
              END ASC,
              t.created_at ASC,
              t.id ASC
            """;

        List<Ticket> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            List<Object> params = where.parameters();
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    // ── Row Mapping ──────────────────────────────────────────────────────────

    private Ticket mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String title = rs.getString("title");
        String description = rs.getString("description");
        String priorityStr = rs.getString("priority");
        String statusStr = rs.getString("status");
        int categoryId = rs.getInt("category_id");
        int submitterId = rs.getInt("submitter_id");
        int assignedStaffId = rs.getInt("assigned_staff_id");
        boolean wasAssignedNull = rs.wasNull();
        String createdAtStr = rs.getString("created_at");
        String updatedAtStr = rs.getString("updated_at");
        String resolvedAtStr = rs.getString("resolved_at");

        return new Ticket.Builder()
                .id(id)
                .title(title)
                .description(description)
                .priority(Priority.valueOf(priorityStr.toUpperCase()))
                .status(TicketStatus.valueOf(statusStr.toUpperCase()))
                .categoryId(categoryId)
                .submitterId(submitterId)
                .assignedStaffId(wasAssignedNull ? null : assignedStaffId)
                .createdAt(LocalDateTime.parse(createdAtStr))
                .updatedAt(updatedAtStr != null ? LocalDateTime.parse(updatedAtStr) : LocalDateTime.parse(createdAtStr))
                .resolvedAt(resolvedAtStr != null ? LocalDateTime.parse(resolvedAtStr) : null)
                .build();
    }
}
