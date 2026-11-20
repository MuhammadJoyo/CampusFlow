package com.campusflow.repository;

import com.campusflow.model.AuditLog;
import com.campusflow.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Repository for managing append-only {@link AuditLog} records against the {@code audit_log} table.
 *
 * <p>Strictly immutable: contains insert and query operations only (no update or delete methods).</p>
 */
public class AuditLogRepository {

    public AuditLogRepository() {
    }

    // ── Standalone API ────────────────────────────────────────────────────────

    public void log(int actorId, String action, String entityType, int entityId, String details) {
        try (Connection conn = DatabaseManager.getConnection()) {
            log(actorId, action, entityType, entityId, details, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to write audit log entry for " + entityType + "#" + entityId, e);
        }
    }

    public List<AuditLog> findAll() {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load audit logs", e);
        }
    }

    public List<AuditLog> findByEntity(String entityType, int entityId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findByEntity(entityType, entityId, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load audit logs for entity: " + entityType + "#" + entityId, e);
        }
    }

    // ── Transactional API (Injected Connection) ──────────────────────────────

    public void log(int actorId, String action, String entityType, int entityId, String details, Connection conn) throws SQLException {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        String sql = """
            INSERT INTO audit_log (actor_id, action, entity_type, entity_id, timestamp, details)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        String nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, actorId);
            ps.setString(2, action.trim());
            ps.setString(3, entityType.trim());
            ps.setInt(4, entityId);
            ps.setString(5, nowIso);
            ps.setString(6, details != null ? details : "");
            ps.executeUpdate();
        }
    }

    public List<AuditLog> findAll(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT id, actor_id, action, entity_type, entity_id, timestamp, details FROM audit_log ORDER BY timestamp DESC, id DESC";
        List<AuditLog> list = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<AuditLog> findByEntity(String entityType, int entityId, Connection conn) throws SQLException {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        String sql = "SELECT id, actor_id, action, entity_type, entity_id, timestamp, details FROM audit_log WHERE entity_type = ? AND entity_id = ? ORDER BY timestamp DESC, id DESC";
        List<AuditLog> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityType.trim());
            ps.setInt(2, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    private AuditLog mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int actorId = rs.getInt("actor_id");
        String action = rs.getString("action");
        String entityType = rs.getString("entity_type");
        int entityId = rs.getInt("entity_id");
        String timestampStr = rs.getString("timestamp");
        String details = rs.getString("details");

        return new AuditLog(id, actorId, action, entityType, entityId,
                LocalDateTime.parse(timestampStr), details != null ? details : "");
    }
}
