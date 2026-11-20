package com.campusflow.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * An immutable audit log entry recording a significant system action.
 *
 * <p>Audit entries are created by service-layer methods for every state-changing
 * operation: ticket creation, status change, assignment, escalation, resolution,
 * user creation/deactivation, and category changes (SRS FR-14).</p>
 *
 * <p>Once created, an audit log entry is never modified or deleted.</p>
 *
 * <p>SCD concepts: SCD-11 (immutable value object), SCD-12 (JDBC row mapping).</p>
 */
public final class AuditLog {

    // ── Fields ───────────────────────────────────────────────────────────────

    private final int id;
    private final int actorId;
    private final String action;
    private final String entityType;
    private final int entityId;
    private final LocalDateTime timestamp;
    private final String details;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs an immutable AuditLog entry.
     *
     * @param id         database primary key (0 for unsaved instances)
     * @param actorId    FK to the user who performed the action
     * @param action     action type string (e.g., "TICKET_CREATED", "STATUS_CHANGED"); non-blank
     * @param entityType entity type string (e.g., "TICKET", "USER", "CATEGORY"); non-blank
     * @param entityId   database id of the affected entity
     * @param timestamp  when the action occurred (non-null)
     * @param details    additional context (non-null, may be empty)
     * @throws NullPointerException     if action, entityType, timestamp, or details is null
     * @throws IllegalArgumentException if action or entityType is blank
     */
    public AuditLog(int id,
                    int actorId,
                    String action,
                    String entityType,
                    int entityId,
                    LocalDateTime timestamp,
                    String details) {
        Objects.requireNonNull(action,     "action must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(timestamp,  "timestamp must not be null");
        Objects.requireNonNull(details,    "details must not be null");

        if (action.isBlank())     throw new IllegalArgumentException("action must not be blank");
        if (entityType.isBlank()) throw new IllegalArgumentException("entityType must not be blank");

        this.id         = id;
        this.actorId    = actorId;
        this.action     = action;
        this.entityType = entityType;
        this.entityId   = entityId;
        this.timestamp  = LocalDateTime.from(timestamp);
        this.details    = details;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** @return database primary key */
    public int getId() { return id; }

    /** @return FK to the acting user */
    public int getActorId() { return actorId; }

    /** @return action type identifier */
    public String getAction() { return action; }

    /** @return entity type (e.g., "TICKET") */
    public String getEntityType() { return entityType; }

    /** @return database id of the affected entity */
    public int getEntityId() { return entityId; }

    /** @return action timestamp (defensive copy) */
    public LocalDateTime getTimestamp() { return LocalDateTime.from(timestamp); }

    /** @return additional context string */
    public String getDetails() { return details; }

    // ── Object overrides ─────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "AuditLog{id=" + id +
               ", actorId=" + actorId +
               ", action='" + action + "'" +
               ", entityType='" + entityType + "'" +
               ", entityId=" + entityId +
               ", timestamp=" + timestamp +
               "}";
    }
}
