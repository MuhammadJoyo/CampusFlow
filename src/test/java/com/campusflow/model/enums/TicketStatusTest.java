package com.campusflow.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TicketStatus#canTransitionTo(TicketStatus)}.
 *
 * Verifies all four approved transitions and rejects all undocumented ones.
 * Sources: SRS FR-06, FR-07, FR-08, FR-15.
 */
@DisplayName("TicketStatus transitions")
class TicketStatusTest {

    // ── Valid transitions ────────────────────────────────────────────────────

    @Test
    @DisplayName("OPEN → IN_PROGRESS is valid (SRS FR-06)")
    void openToInProgress() {
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("IN_PROGRESS → ESCALATED is valid (SRS FR-07)")
    void inProgressToEscalated() {
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.ESCALATED));
    }

    @Test
    @DisplayName("IN_PROGRESS → RESOLVED is valid (SRS FR-07)")
    void inProgressToResolved() {
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.RESOLVED));
    }

    @Test
    @DisplayName("RESOLVED → CLOSED is valid (SRS FR-15)")
    void resolvedToClosed() {
        assertTrue(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.CLOSED));
    }

    // ── Invalid transitions from OPEN ────────────────────────────────────────

    @Test
    @DisplayName("OPEN → RESOLVED is invalid")
    void openToResolvedInvalid() {
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.RESOLVED));
    }

    @Test
    @DisplayName("OPEN → CLOSED is invalid")
    void openToClosedInvalid() {
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.CLOSED));
    }

    @Test
    @DisplayName("OPEN → ESCALATED is invalid")
    void openToEscalatedInvalid() {
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.ESCALATED));
    }

    // ── CLOSED is a terminal state ───────────────────────────────────────────

    @ParameterizedTest(name = "CLOSED → {0} is invalid")
    @EnumSource(TicketStatus.class)
    @DisplayName("CLOSED has no valid outgoing transitions (SRS FR-15: immutable)")
    void closedHasNoValidTransitions(TicketStatus target) {
        assertFalse(TicketStatus.CLOSED.canTransitionTo(target));
    }

    // ── ESCALATED standard workflow is a dead-end ────────────────────────────

    @ParameterizedTest(name = "ESCALATED → {0} is invalid in standard workflow")
    @EnumSource(TicketStatus.class)
    @DisplayName("ESCALATED has no standard outgoing transitions (admin override at service layer)")
    void escalatedHasNoStandardTransitions(TicketStatus target) {
        assertFalse(TicketStatus.ESCALATED.canTransitionTo(target));
    }

    // ── allowedTransitions() completeness ────────────────────────────────────

    @Test
    @DisplayName("OPEN allowedTransitions contains exactly IN_PROGRESS")
    void openAllowedTransitions() {
        var transitions = TicketStatus.OPEN.allowedTransitions();
        assertEquals(1, transitions.size());
        assertTrue(transitions.contains(TicketStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("IN_PROGRESS allowedTransitions contains exactly ESCALATED and RESOLVED")
    void inProgressAllowedTransitions() {
        var transitions = TicketStatus.IN_PROGRESS.allowedTransitions();
        assertEquals(2, transitions.size());
        assertTrue(transitions.contains(TicketStatus.ESCALATED));
        assertTrue(transitions.contains(TicketStatus.RESOLVED));
    }
}
