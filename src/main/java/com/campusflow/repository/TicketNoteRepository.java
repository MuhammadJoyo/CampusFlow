package com.campusflow.repository;

import com.campusflow.model.TicketNote;
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
import java.util.Optional;

/**
 * Repository for managing {@link TicketNote} persistence against the {@code ticket_notes} table.
 *
 * <p>Separated from TicketRepository in accordance with the Single Responsibility Principle (SRP).</p>
 */
public class TicketNoteRepository {

    public TicketNoteRepository() {
    }

    // ── Standalone API ────────────────────────────────────────────────────────

    public Optional<TicketNote> findById(int id) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findById(id, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find ticket note by id: " + id, e);
        }
    }

    public List<TicketNote> findByTicketId(int ticketId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findByTicketId(ticketId, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load notes for ticket id: " + ticketId, e);
        }
    }

    public TicketNote save(TicketNote note) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return save(note, conn);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save ticket note for ticket: " + note.getTicketId(), e);
        }
    }

    // ── Transactional API (Injected Connection) ──────────────────────────────

    public Optional<TicketNote> findById(int id, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT id, ticket_id, author_id, note, created_at FROM ticket_notes WHERE id = ?";
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

    public List<TicketNote> findByTicketId(int ticketId, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String sql = "SELECT id, ticket_id, author_id, note, created_at FROM ticket_notes WHERE ticket_id = ? ORDER BY created_at ASC, id ASC";
        List<TicketNote> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public TicketNote save(TicketNote note, Connection conn) throws SQLException {
        Objects.requireNonNull(note, "TicketNote must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        String sql = "INSERT INTO ticket_notes (ticket_id, author_id, note, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, note.getTicketId());
            ps.setInt(2, note.getAuthorId());
            ps.setString(3, note.getNote());
            ps.setString(4, note.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int generatedId = keys.getInt(1);
                    return new TicketNote(generatedId, note.getTicketId(), note.getAuthorId(), note.getNote(), note.getCreatedAt());
                }
                throw new SQLException("Failed to obtain generated ID for ticket note");
            }
        }
    }

    private TicketNote mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int ticketId = rs.getInt("ticket_id");
        int authorId = rs.getInt("author_id");
        String note = rs.getString("note");
        String createdAtStr = rs.getString("created_at");

        return new TicketNote(id, ticketId, authorId, note, LocalDateTime.parse(createdAtStr));
    }
}
