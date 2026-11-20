package com.campusflow.model;

import com.campusflow.model.enums.Priority;
import com.campusflow.model.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Core domain entity representing a single campus service request (ticket).
 *
 * <h2>Natural Ordering (Comparable)</h2>
 * <p>Tickets implement {@link Comparable} so they can be held in a
 * {@link java.util.concurrent.PriorityBlockingQueue} without an external comparator.
 * The ordering is:</p>
 * <ol>
 *   <li>By {@link Priority#getWeight()} ascending — CRITICAL (0) before HIGH (1)
 *       before MEDIUM (2) before LOW (3).</li>
 *   <li>By {@code createdAt} ascending — earlier tickets first (FIFO within priority).</li>
 *   <li>By {@code id} ascending — deterministic tiebreaker; also ensures
 *       {@code compareTo() == 0} iff {@code equals() == true}.</li>
 * </ol>
 *
 * <h2>Consistency: equals vs compareTo</h2>
 * <p>{@code equals} is based solely on {@code id}. The {@code id} tiebreaker in
 * {@code compareTo} guarantees that {@code compareTo(other) == 0} implies
 * {@code equals(other) == true}, satisfying the {@link Comparable} contract.</p>
 *
 * <h2>Nullable Fields</h2>
 * <ul>
 *   <li>{@code assignedStaffId} — {@code null} until a staff member claims the ticket.</li>
 *   <li>{@code resolvedAt} — {@code null} until the ticket is resolved.</li>
 * </ul>
 *
 * <h2>Construction</h2>
 * <p>Use the {@link Builder} to construct instances. The builder validates all
 * required fields before creating the object.</p>
 *
 * <p>SCD concepts: SCD-05 (PriorityBlockingQueue ordering), SCD-10 (Comparable),
 * SCD-11 (Builder pattern, encapsulation).</p>
 */
public final class Ticket implements Comparable<Ticket> {

    // ── Fields ───────────────────────────────────────────────────────────────

    private final int id;
    private final String title;
    private final String description;
    private final Priority priority;
    private TicketStatus status;              // mutable — updated via service layer
    private final int categoryId;
    private final int submitterId;
    private Integer assignedStaffId;          // nullable boxed Integer
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;          // updated on every status change
    private LocalDateTime resolvedAt;         // set when status becomes RESOLVED

    // ── Constructor (private — use Builder) ──────────────────────────────────

    private Ticket(Builder builder) {
        this.id              = builder.id;
        this.title           = builder.title;
        this.description     = builder.description;
        this.priority        = builder.priority;
        this.status          = builder.status;
        this.categoryId      = builder.categoryId;
        this.submitterId     = builder.submitterId;
        this.assignedStaffId = builder.assignedStaffId;
        this.createdAt       = LocalDateTime.from(builder.createdAt);
        this.updatedAt       = builder.updatedAt != null
                               ? LocalDateTime.from(builder.updatedAt)
                               : LocalDateTime.from(builder.createdAt);
        this.resolvedAt      = builder.resolvedAt != null
                               ? LocalDateTime.from(builder.resolvedAt)
                               : null;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** @return database primary key */
    public int getId() { return id; }

    /** @return ticket title */
    public String getTitle() { return title; }

    /** @return ticket description */
    public String getDescription() { return description; }

    /** @return current priority */
    public Priority getPriority() { return priority; }

    /** @return current status */
    public TicketStatus getStatus() { return status; }

    /** @return FK to the category */
    public int getCategoryId() { return categoryId; }

    /** @return FK to the submitting student */
    public int getSubmitterId() { return submitterId; }

    /**
     * @return FK to the assigned staff member, or {@code null} if unassigned
     */
    public Integer getAssignedStaffId() { return assignedStaffId; }

    /** @return ticket creation timestamp (defensive copy) */
    public LocalDateTime getCreatedAt() { return LocalDateTime.from(createdAt); }

    /** @return timestamp of last status update (defensive copy) */
    public LocalDateTime getUpdatedAt() { return LocalDateTime.from(updatedAt); }

    /**
     * @return resolution timestamp, or {@code null} if not yet resolved
     */
    public LocalDateTime getResolvedAt() {
        return resolvedAt != null ? LocalDateTime.from(resolvedAt) : null;
    }

    // ── Mutation (called only by service layer) ──────────────────────────────

    /**
     * Updates the ticket status and refreshes the updatedAt timestamp.
     * Callers must have already validated the transition via
     * {@link TicketStatus#canTransitionTo(TicketStatus)}.
     *
     * @param newStatus the new status
     * @param now       current server time
     */
    public void setStatus(TicketStatus newStatus, LocalDateTime now) {
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        Objects.requireNonNull(now,       "now must not be null");
        this.status    = newStatus;
        this.updatedAt = now;
        if (newStatus == TicketStatus.RESOLVED) {
            this.resolvedAt = now;
        }
    }

    /**
     * Assigns a staff member to this ticket.
     *
     * @param staffId staff member FK (must be &gt; 0)
     */
    public void assignTo(int staffId) {
        if (staffId <= 0) throw new IllegalArgumentException("staffId must be > 0, got: " + staffId);
        this.assignedStaffId = staffId;
    }

    /**
     * Clears the staff assignment (used during escalation re-routing).
     */
    public void unassign() {
        this.assignedStaffId = null;
    }

    // ── Comparable ───────────────────────────────────────────────────────────

    /**
     * Natural ordering for use with {@link java.util.concurrent.PriorityBlockingQueue}
     * (min-heap: smallest value dequeued first).
     *
     * <ol>
     *   <li>Priority weight ascending (CRITICAL=0 is smallest → dequeued first).</li>
     *   <li>createdAt ascending (earlier ticket is "less than" a later one).</li>
     *   <li>id ascending (deterministic tiebreaker; ensures compareTo==0 iff equals==true).</li>
     * </ol>
     *
     * @param other the ticket to compare against
     * @return negative if this should be dequeued before other; positive if after; 0 only if same id
     */
    @Override
    public int compareTo(Ticket other) {
        Objects.requireNonNull(other, "Cannot compare to null Ticket");

        // 1. Higher urgency (lower weight) comes first
        int cmp = Integer.compare(this.priority.getWeight(), other.priority.getWeight());
        if (cmp != 0) return cmp;

        // 2. Earlier creation time comes first (FIFO within same priority)
        cmp = this.createdAt.compareTo(other.createdAt);
        if (cmp != 0) return cmp;

        // 3. Deterministic tiebreaker: smaller id first
        //    Guarantees: compareTo()==0  ⟺  id==id  ⟺  equals()==true
        return Integer.compare(this.id, other.id);
    }

    // ── Object overrides ─────────────────────────────────────────────────────

    /** Equality based on database primary key only. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ticket other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "Ticket{id=" + id +
               ", title='" + title + "'" +
               ", priority=" + priority +
               ", status=" + status +
               ", submitterId=" + submitterId +
               "}";
    }

    // ════════════════════════════════════════════════════════════════════════
    // Static Inner Builder
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Builder for {@link Ticket}.
     *
     * <p>Required fields (must be set before calling {@link #build()}):
     * {@code title}, {@code description}, {@code priority}, {@code status},
     * {@code categoryId} (&gt;0), {@code submitterId} (&gt;0), {@code createdAt}.</p>
     *
     * <p>Optional fields: {@code id} (defaults to 0 for transient instances),
     * {@code assignedStaffId} (nullable), {@code updatedAt} (defaults to createdAt),
     * {@code resolvedAt} (nullable).</p>
     */
    public static final class Builder {

        // Required
        private String title;
        private String description;
        private Priority priority;
        private TicketStatus status;
        private int categoryId;
        private int submitterId;
        private LocalDateTime createdAt;

        // Optional
        private int id = 0;
        private Integer assignedStaffId = null;
        private LocalDateTime updatedAt = null;
        private LocalDateTime resolvedAt = null;

        public Builder id(int id)                            { this.id = id;                         return this; }
        public Builder title(String title)                   { this.title = title;                   return this; }
        public Builder description(String description)       { this.description = description;       return this; }
        public Builder priority(Priority priority)           { this.priority = priority;             return this; }
        public Builder status(TicketStatus status)           { this.status = status;                 return this; }
        public Builder categoryId(int categoryId)            { this.categoryId = categoryId;         return this; }
        public Builder submitterId(int submitterId)          { this.submitterId = submitterId;       return this; }
        public Builder assignedStaffId(Integer staffId)      { this.assignedStaffId = staffId;      return this; }
        public Builder createdAt(LocalDateTime createdAt)    { this.createdAt = createdAt;           return this; }
        public Builder updatedAt(LocalDateTime updatedAt)    { this.updatedAt = updatedAt;           return this; }
        public Builder resolvedAt(LocalDateTime resolvedAt)  { this.resolvedAt = resolvedAt;         return this; }

        /**
         * Validates all required fields and constructs the {@link Ticket}.
         *
         * @return a new, validated Ticket instance
         * @throws NullPointerException     if title, description, priority, status, or createdAt is null
         * @throws IllegalArgumentException if title is blank, categoryId ≤ 0, or submitterId ≤ 0
         */
        public Ticket build() {
            Objects.requireNonNull(title,       "Ticket title must not be null");
            Objects.requireNonNull(description, "Ticket description must not be null");
            Objects.requireNonNull(priority,    "Ticket priority must not be null");
            Objects.requireNonNull(status,      "Ticket status must not be null");
            Objects.requireNonNull(createdAt,   "Ticket createdAt must not be null");

            if (title.isBlank())    throw new IllegalArgumentException("Ticket title must not be blank");
            if (categoryId <= 0)    throw new IllegalArgumentException("categoryId must be > 0, got: " + categoryId);
            if (submitterId <= 0)   throw new IllegalArgumentException("submitterId must be > 0, got: " + submitterId);

            return new Ticket(this);
        }
    }
}
