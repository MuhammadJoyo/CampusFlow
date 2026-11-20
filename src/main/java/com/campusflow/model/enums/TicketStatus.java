package com.campusflow.model.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle states for a CampusFlow ticket.
 *
 * <h2>Permitted Transitions (SRS FR-06, FR-07, FR-08, FR-15)</h2>
 * <pre>
 *  OPEN ──────────────────► IN_PROGRESS
 *  IN_PROGRESS ───────────► ESCALATED
 *  IN_PROGRESS ───────────► RESOLVED
 *  RESOLVED ──────────────► CLOSED
 *
 *  ESCALATED  → {} (terminal for standard workflow; Admin override at service layer)
 *  CLOSED     → {} (terminal; immutable per SRS FR-15)
 * </pre>
 *
 * <p>These are the <em>only</em> four transitions specified in approved documentation.
 * Admin overrides (SRS FR-17) bypass these rules at the service layer and are
 * <em>not</em> encoded here to keep the enum aligned with the standard workflow only.</p>
 *
 * <p>SCD concepts: SCD-10 (Polymorphism — each status knows its valid successors),
 * SCD-11 (Open/Closed Principle).</p>
 */
public enum TicketStatus {

    OPEN,
    IN_PROGRESS,
    ESCALATED,
    RESOLVED,
    CLOSED;

    // ── Transition Table ─────────────────────────────────────────────────────

    /**
     * Returns {@code true} if transitioning from {@code this} status to {@code next}
     * is permitted by the standard workflow documented in SRS FR-06, FR-07, FR-15.
     *
     * <p>Admin overrides (FR-17) are enforced at the service layer and must not
     * call this method — they bypass standard transition rules by design.</p>
     *
     * @param next the target status
     * @return {@code true} if the transition is valid
     */
    public boolean canTransitionTo(TicketStatus next) {
        return allowedTransitions().contains(next);
    }

    /**
     * Returns the set of statuses this status may legally transition to.
     * Returns an empty set for terminal states (ESCALATED standard path, CLOSED).
     *
     * @return immutable set of valid next statuses
     */
    public Set<TicketStatus> allowedTransitions() {
        return switch (this) {
            case OPEN        -> EnumSet.of(IN_PROGRESS);
            case IN_PROGRESS -> EnumSet.of(ESCALATED, RESOLVED);
            case RESOLVED    -> EnumSet.of(CLOSED);
            case ESCALATED,
                 CLOSED      -> EnumSet.noneOf(TicketStatus.class);
        };
    }
}
