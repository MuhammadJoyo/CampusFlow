package com.campusflow.model;

import com.campusflow.model.enums.Priority;
import com.campusflow.model.enums.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.PriorityBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Ticket}: Comparable ordering, Builder validation,
 * equals/hashCode, and nullable field defaults.
 *
 * SCD concepts exercised: SCD-05, SCD-10, SCD-11.
 */
@DisplayName("Ticket domain entity")
class TicketTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2024, 1, 1, 12, 0);

    // ── Helper ───────────────────────────────────────────────────────────────

    private Ticket ticket(int id, Priority priority, LocalDateTime createdAt) {
        return new Ticket.Builder()
                .id(id)
                .title("Test Ticket " + id)
                .description("Description for ticket " + id)
                .priority(priority)
                .status(TicketStatus.OPEN)
                .categoryId(1)
                .submitterId(1)
                .createdAt(createdAt)
                .build();
    }

    // ── compareTo: priority ordering ─────────────────────────────────────────

    @Test
    @DisplayName("CRITICAL ticket compares before HIGH ticket (same createdAt)")
    void criticalBeforeHigh() {
        Ticket critical = ticket(1, Priority.CRITICAL, BASE_TIME);
        Ticket high     = ticket(2, Priority.HIGH,     BASE_TIME);
        assertTrue(critical.compareTo(high) < 0, "CRITICAL should come before HIGH");
    }

    @Test
    @DisplayName("HIGH ticket compares before MEDIUM ticket (same createdAt)")
    void highBeforeMedium() {
        Ticket high   = ticket(1, Priority.HIGH,   BASE_TIME);
        Ticket medium = ticket(2, Priority.MEDIUM, BASE_TIME);
        assertTrue(high.compareTo(medium) < 0);
    }

    @Test
    @DisplayName("MEDIUM ticket compares before LOW ticket (same createdAt)")
    void mediumBeforeLow() {
        Ticket medium = ticket(1, Priority.MEDIUM, BASE_TIME);
        Ticket low    = ticket(2, Priority.LOW,    BASE_TIME);
        assertTrue(medium.compareTo(low) < 0);
    }

    @Test
    @DisplayName("CRITICAL ticket compares before LOW ticket (same createdAt)")
    void criticalBeforeLow() {
        Ticket critical = ticket(1, Priority.CRITICAL, BASE_TIME);
        Ticket low      = ticket(2, Priority.LOW,      BASE_TIME);
        assertTrue(critical.compareTo(low) < 0);
    }

    // ── compareTo: timestamp tiebreaker ─────────────────────────────────────

    @Test
    @DisplayName("Same priority: earlier createdAt compares before later")
    void samePriorityEarlierFirst() {
        Ticket earlier = ticket(1, Priority.HIGH, BASE_TIME);
        Ticket later   = ticket(2, Priority.HIGH, BASE_TIME.plusMinutes(5));
        assertTrue(earlier.compareTo(later) < 0);
    }

    @Test
    @DisplayName("Same priority and createdAt: lower id compares first (deterministic tiebreaker)")
    void samePriorityAndTimeIdTiebreaker() {
        Ticket t1 = ticket(1, Priority.HIGH, BASE_TIME);
        Ticket t2 = ticket(2, Priority.HIGH, BASE_TIME);
        assertTrue(t1.compareTo(t2) < 0);
    }

    @Test
    @DisplayName("compareTo == 0 iff same id (consistent with equals)")
    void compareToZeroConsistentWithEquals() {
        Ticket t1 = ticket(5, Priority.MEDIUM, BASE_TIME);
        Ticket t2 = ticket(5, Priority.MEDIUM, BASE_TIME);
        assertEquals(0, t1.compareTo(t2));
        assertEquals(t1, t2);
    }

    // ── PriorityBlockingQueue integration ────────────────────────────────────

    @Test
    @DisplayName("PriorityBlockingQueue dequeues CRITICAL ticket before LOW ticket")
    void queueDequeuesCriticalFirst() {
        PriorityBlockingQueue<Ticket> queue = new PriorityBlockingQueue<>();
        Ticket low      = ticket(1, Priority.LOW,      BASE_TIME);
        Ticket critical = ticket(2, Priority.CRITICAL, BASE_TIME);
        Ticket medium   = ticket(3, Priority.MEDIUM,   BASE_TIME);

        queue.add(low);
        queue.add(critical);
        queue.add(medium);

        assertEquals(critical, queue.poll());
        assertEquals(medium,   queue.poll());
        assertEquals(low,      queue.poll());
    }

    // ── equals / hashCode ────────────────────────────────────────────────────

    @Test
    @DisplayName("Two tickets with same id are equal regardless of other fields")
    void equalsById() {
        Ticket t1 = ticket(42, Priority.LOW,      BASE_TIME);
        Ticket t2 = ticket(42, Priority.CRITICAL, BASE_TIME.plusDays(1));
        assertEquals(t1, t2);
    }

    @Test
    @DisplayName("Two tickets with different ids are not equal")
    void notEqualDifferentIds() {
        Ticket t1 = ticket(1, Priority.HIGH, BASE_TIME);
        Ticket t2 = ticket(2, Priority.HIGH, BASE_TIME);
        assertNotEquals(t1, t2);
    }

    @Test
    @DisplayName("hashCode is consistent with equals")
    void hashCodeConsistentWithEquals() {
        Ticket t1 = ticket(7, Priority.MEDIUM, BASE_TIME);
        Ticket t2 = ticket(7, Priority.MEDIUM, BASE_TIME);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    // ── Nullable defaults ────────────────────────────────────────────────────

    @Test
    @DisplayName("assignedStaffId is null by default")
    void assignedStaffIdNullByDefault() {
        Ticket t = ticket(1, Priority.LOW, BASE_TIME);
        assertNull(t.getAssignedStaffId());
    }

    @Test
    @DisplayName("resolvedAt is null by default")
    void resolvedAtNullByDefault() {
        Ticket t = ticket(1, Priority.LOW, BASE_TIME);
        assertNull(t.getResolvedAt());
    }

    // ── Builder validation ───────────────────────────────────────────────────

    @Test
    @DisplayName("Builder rejects null title")
    void builderRejectsNullTitle() {
        assertThrows(NullPointerException.class, () ->
            new Ticket.Builder()
                .description("desc")
                .priority(Priority.LOW)
                .status(TicketStatus.OPEN)
                .categoryId(1)
                .submitterId(1)
                .createdAt(BASE_TIME)
                .build());
    }

    @Test
    @DisplayName("Builder rejects blank title")
    void builderRejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class, () ->
            new Ticket.Builder()
                .title("   ")
                .description("desc")
                .priority(Priority.LOW)
                .status(TicketStatus.OPEN)
                .categoryId(1)
                .submitterId(1)
                .createdAt(BASE_TIME)
                .build());
    }

    @Test
    @DisplayName("Builder rejects null priority")
    void builderRejectsNullPriority() {
        assertThrows(NullPointerException.class, () ->
            new Ticket.Builder()
                .title("Title")
                .description("desc")
                .status(TicketStatus.OPEN)
                .categoryId(1)
                .submitterId(1)
                .createdAt(BASE_TIME)
                .build());
    }

    @Test
    @DisplayName("Builder rejects categoryId <= 0")
    void builderRejectsInvalidCategoryId() {
        assertThrows(IllegalArgumentException.class, () ->
            new Ticket.Builder()
                .title("Title")
                .description("desc")
                .priority(Priority.LOW)
                .status(TicketStatus.OPEN)
                .categoryId(0)
                .submitterId(1)
                .createdAt(BASE_TIME)
                .build());
    }

    @Test
    @DisplayName("Builder rejects submitterId <= 0")
    void builderRejectsInvalidSubmitterId() {
        assertThrows(IllegalArgumentException.class, () ->
            new Ticket.Builder()
                .title("Title")
                .description("desc")
                .priority(Priority.LOW)
                .status(TicketStatus.OPEN)
                .categoryId(1)
                .submitterId(-1)
                .createdAt(BASE_TIME)
                .build());
    }

    @Test
    @DisplayName("Builder rejects null createdAt")
    void builderRejectsNullCreatedAt() {
        assertThrows(NullPointerException.class, () ->
            new Ticket.Builder()
                .title("Title")
                .description("desc")
                .priority(Priority.LOW)
                .status(TicketStatus.OPEN)
                .categoryId(1)
                .submitterId(1)
                .build());
    }
}
