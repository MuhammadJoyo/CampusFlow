package com.campusflow.model;

import java.util.Objects;

/**
 * Represents a campus department that handles a category of service tickets.
 *
 * <p>Departments are loaded from the database at server startup and are used to
 * initialise the per-department {@code PriorityBlockingQueue} in
 * {@code TicketQueueManager} (ADR-007).</p>
 *
 * <p>This class is <strong>immutable</strong>: all fields are final and there
 * are no setters. The only way to change department data is to reload from the
 * database.</p>
 *
 * <p>SCD concepts: SCD-11 (immutable value object), SCD-12 (JDBC row mapping).</p>
 */
public final class Department {

    // ── Fields ───────────────────────────────────────────────────────────────

    private final int id;
    private final String name;
    private final String description;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs an immutable Department.
     *
     * @param id          database primary key
     * @param name        department name (non-null, non-blank)
     * @param description department description (non-null, may be empty)
     * @throws NullPointerException     if name or description is null
     * @throws IllegalArgumentException if name is blank
     */
    public Department(int id, String name, String description) {
        Objects.requireNonNull(name,        "Department name must not be null");
        Objects.requireNonNull(description, "Department description must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("Department name must not be blank");

        this.id          = id;
        this.name        = name;
        this.description = description;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** @return database primary key */
    public int getId() { return id; }

    /** @return department name */
    public String getName() { return name; }

    /** @return department description */
    public String getDescription() { return description; }

    // ── Object overrides ─────────────────────────────────────────────────────

    /** Equality based on database primary key. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Department other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "Department{id=" + id + ", name='" + name + "'}";
    }
}
