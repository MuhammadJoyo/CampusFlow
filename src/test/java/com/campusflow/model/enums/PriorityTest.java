package com.campusflow.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.PriorityBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Priority}.
 *
 * Verifies weight-based ordering, bump() behaviour, and
 * PriorityBlockingQueue integration (SCD-05, SCD-10).
 */
@DisplayName("Priority enum")
class PriorityTest {

    // ── Weight ordering ──────────────────────────────────────────────────────

    @Test
    @DisplayName("CRITICAL has lower weight than HIGH")
    void criticalWeightLowerThanHigh() {
        assertTrue(Priority.CRITICAL.getWeight() < Priority.HIGH.getWeight());
    }

    @Test
    @DisplayName("HIGH has lower weight than MEDIUM")
    void highWeightLowerThanMedium() {
        assertTrue(Priority.HIGH.getWeight() < Priority.MEDIUM.getWeight());
    }

    @Test
    @DisplayName("MEDIUM has lower weight than LOW")
    void mediumWeightLowerThanLow() {
        assertTrue(Priority.MEDIUM.getWeight() < Priority.LOW.getWeight());
    }

    @Test
    @DisplayName("CRITICAL weight is 0 (highest urgency)")
    void criticalWeightIsZero() {
        assertEquals(0, Priority.CRITICAL.getWeight());
    }

    @Test
    @DisplayName("LOW weight is 3 (lowest urgency)")
    void lowWeightIsThree() {
        assertEquals(3, Priority.LOW.getWeight());
    }

    @Test
    @DisplayName("comparePriority: CRITICAL < HIGH (smaller = higher urgency)")
    void comparePriorityCriticalBeforeHigh() {
        assertTrue(Priority.CRITICAL.comparePriority(Priority.HIGH) < 0);
    }

    @Test
    @DisplayName("comparePriority: same priority returns 0")
    void comparePrioritySameReturnsZero() {
        assertEquals(0, Priority.HIGH.comparePriority(Priority.HIGH));
    }

    // ── bump() ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bump: LOW → MEDIUM")
    void bumpLowToMedium() {
        assertEquals(Priority.MEDIUM, Priority.LOW.bump());
    }

    @Test
    @DisplayName("bump: MEDIUM → HIGH")
    void bumpMediumToHigh() {
        assertEquals(Priority.HIGH, Priority.MEDIUM.bump());
    }

    @Test
    @DisplayName("bump: HIGH → CRITICAL")
    void bumpHighToCritical() {
        assertEquals(Priority.CRITICAL, Priority.HIGH.bump());
    }

    @Test
    @DisplayName("bump: CRITICAL → CRITICAL (no-op, no exception)")
    void bumpCriticalIsSelf() {
        assertDoesNotThrow(() -> Priority.CRITICAL.bump());
        assertEquals(Priority.CRITICAL, Priority.CRITICAL.bump());
    }

    // ── PriorityBlockingQueue integration ────────────────────────────────────

    @Test
    @DisplayName("PriorityBlockingQueue dequeues CRITICAL before HIGH before MEDIUM before LOW")
    void priorityBlockingQueueOrdersCorrectly() {
        PriorityBlockingQueue<Priority> queue = new PriorityBlockingQueue<>(
                4, (a, b) -> Integer.compare(a.getWeight(), b.getWeight()));

        queue.add(Priority.LOW);
        queue.add(Priority.HIGH);
        queue.add(Priority.CRITICAL);
        queue.add(Priority.MEDIUM);

        assertEquals(Priority.CRITICAL, queue.poll());
        assertEquals(Priority.HIGH,     queue.poll());
        assertEquals(Priority.MEDIUM,   queue.poll());
        assertEquals(Priority.LOW,      queue.poll());
    }
}
