package com.campusflow.model.enums;

/**
 * Ticket urgency levels for CampusFlow.
 *
 * <h2>Ordering Contract</h2>
 * <p>CampusFlow uses a {@link java.util.concurrent.PriorityBlockingQueue}, which is a
 * <em>min-heap</em>: the element with the <strong>smallest</strong> {@code compareTo}
 * value is dequeued first. To ensure CRITICAL tickets are served before all others,
 * each constant carries an integer {@code weight} where
 * {@code CRITICAL=0 < HIGH=1 < MEDIUM=2 < LOW=3}.
 * {@code comparePriority} delegates to {@code Integer.compare(this.weight, other.weight)},
 * so CRITICAL (weight=0) always leaves the queue first.</p>
 *
 * <p><strong>Important</strong>: do <em>not</em> rely on {@link #ordinal()} for
 * ordering — the declaration order is LOW, MEDIUM, HIGH, CRITICAL (ascending urgency),
 * which gives LOW ordinal=0, the opposite of what the queue requires.</p>
 *
 * <h2>bump() Contract</h2>
 * <p>Escalation (SRS FR-08) promotes a ticket by one urgency level.
 * Calling {@code bump()} on CRITICAL returns CRITICAL itself (no exception, no-op).</p>
 *
 * <p>SCD concepts: SCD-05 (PriorityBlockingQueue ordering), SCD-10 (Comparable).</p>
 */
public enum Priority {

    // Declaration order: ascending urgency (LOW first).
    // The 'weight' field carries the min-heap key (inverse of urgency).
    LOW(3),
    MEDIUM(2),
    HIGH(1),
    CRITICAL(0);

    // ── Fields ──────────────────────────────────────────────────────────────

    /**
     * Min-heap key: smaller weight → higher urgency → dequeued first.
     * CRITICAL=0, HIGH=1, MEDIUM=2, LOW=3.
     */
    private final int weight;

    // ── Constructor ─────────────────────────────────────────────────────────

    Priority(int weight) {
        this.weight = weight;
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the min-heap key for this priority level.
     * Lower value = higher urgency = dequeued first.
     *
     * @return weight (0–3)
     */
    public int getWeight() {
        return weight;
    }

    // ── Business Methods ─────────────────────────────────────────────────────

    /**
     * Returns the next higher priority level (SRS FR-08: escalation bump).
     * <ul>
     *   <li>LOW → MEDIUM</li>
     *   <li>MEDIUM → HIGH</li>
     *   <li>HIGH → CRITICAL</li>
     *   <li>CRITICAL → CRITICAL (already maximum; no-op)</li>
     * </ul>
     *
     * @return bumped priority, or {@code CRITICAL} if already at maximum
     */
    public Priority bump() {
        return switch (this) {
            case LOW      -> MEDIUM;
            case MEDIUM   -> HIGH;
            case HIGH     -> CRITICAL;
            case CRITICAL -> CRITICAL;
        };
    }

    /**
     * Natural ordering for use with {@link java.util.concurrent.PriorityBlockingQueue}.
     * Compares by {@link #weight}: smaller weight = higher urgency = "less than"
     * in the min-heap sense.
     *
     * @param other the other Priority to compare
     * @return negative if this has higher urgency, positive if lower, 0 if equal
     */
    public int comparePriority(Priority other) {
        return Integer.compare(this.weight, other.weight);
    }
}
