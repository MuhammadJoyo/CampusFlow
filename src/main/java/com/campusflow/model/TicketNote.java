package com.campusflow.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * An immutable note attached to a ticket, recording an action or status update.
 *
 * <h2>Validation Policy</h2>
 * <p>The constructor validates that {@code note} is non-null and non-blank.
 * The minimum 10-character length rule stated in SRS FR-07
 * ("Each update requires a note (min 10 chars) explaining the action")
 * is a <em>service workflow constraint</em> enforced by {@code TicketService.updateStatus()},
 * not an entity invariant. This keeps the domain model free of business rules
 * that belong to the service layer (strict layer separation per ARCH-001).</p>
 *
 * <p>SCD concepts: SCD-11 (immutable value object, encapsulation).</p>
 */
public final class TicketNote {

    // ── Fields ───────────────────────────────────────────────────────────────

    private final int id;
    private final int ticketId;
    private final int authorId;
    private final String note;
    private final LocalDateTime createdAt;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs an immutable TicketNote.
     *
     * @param id        database primary key (0 for unsaved instances)
     * @param ticketId  FK to the ticket this note belongs to (must be &gt; 0)
     * @param authorId  FK to the user who wrote the note (must be &gt; 0)
     * @param note      note text (non-null, non-blank)
     * @param createdAt creation timestamp (non-null)
     * @throws NullPointerException     if note or createdAt is null
     * @throws IllegalArgumentException if note is blank, ticketId ≤ 0, or authorId ≤ 0
     */
    public TicketNote(int id, int ticketId, int authorId, String note, LocalDateTime createdAt) {
        Objects.requireNonNull(note,      "note must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (note.isBlank())  throw new IllegalArgumentException("note must not be blank");
        if (ticketId <= 0)   throw new IllegalArgumentException("ticketId must be > 0, got: " + ticketId);
        if (authorId <= 0)   throw new IllegalArgumentException("authorId must be > 0, got: " + authorId);

        this.id        = id;
        this.ticketId  = ticketId;
        this.authorId  = authorId;
        this.note      = note;
        this.createdAt = LocalDateTime.from(createdAt);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** @return database primary key */
    public int getId() { return id; }

    /** @return FK to the owning ticket */
    public int getTicketId() { return ticketId; }

    /** @return FK to the note author */
    public int getAuthorId() { return authorId; }

    /** @return note text */
    public String getNote() { return note; }

    /** @return creation timestamp (defensive copy) */
    public LocalDateTime getCreatedAt() { return LocalDateTime.from(createdAt); }

    // ── Object overrides ─────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "TicketNote{id=" + id +
               ", ticketId=" + ticketId +
               ", authorId=" + authorId +
               ", note='" + note + "'" +
               "}";
    }
}
