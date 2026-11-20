package com.campusflow.model;

import java.util.Objects;

/**
 * Represents a complaint category within a department.
 *
 * <p>Each category belongs to exactly one department (via {@code departmentId}).
 * When a student submits a ticket, they select a category; the server's
 * {@code RoutingService} maps the category to its department and routes the
 * ticket to that department's queue (SRS FR-05).</p>
 *
 * <p>Deactivating a category (setting {@code isActive=false}) prevents new tickets
 * from using it without affecting existing tickets (SRS FR-16).</p>
 *
 * <p>This class is <strong>immutable</strong>: all fields are final.
 * Updates are reflected by loading a new instance from the database.</p>
 *
 * <p>SCD concepts: SCD-11 (immutable value object), SCD-12 (JDBC row mapping).</p>
 */
public final class Category {

    // ── Fields ───────────────────────────────────────────────────────────────

    private final int id;
    private final String name;
    private final String description;
    private final int departmentId;
    private final boolean isActive;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs an immutable Category.
     *
     * @param id           database primary key
     * @param name         category name (non-null, non-blank)
     * @param description  category description (non-null, may be empty)
     * @param departmentId FK to departments table (must be &gt; 0)
     * @param isActive     whether the category accepts new tickets
     * @throws NullPointerException     if name or description is null
     * @throws IllegalArgumentException if name is blank or departmentId ≤ 0
     */
    public Category(int id, String name, String description, int departmentId, boolean isActive) {
        Objects.requireNonNull(name,        "Category name must not be null");
        Objects.requireNonNull(description, "Category description must not be null");
        if (name.isBlank())    throw new IllegalArgumentException("Category name must not be blank");
        if (departmentId <= 0) throw new IllegalArgumentException("departmentId must be > 0, got: " + departmentId);

        this.id           = id;
        this.name         = name;
        this.description  = description;
        this.departmentId = departmentId;
        this.isActive     = isActive;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** @return database primary key */
    public int getId() { return id; }

    /** @return category name */
    public String getName() { return name; }

    /** @return category description */
    public String getDescription() { return description; }

    /** @return FK to the owning department */
    public int getDepartmentId() { return departmentId; }

    /** @return {@code true} if the category accepts new ticket submissions */
    public boolean isActive() { return isActive; }

    // ── Object overrides ─────────────────────────────────────────────────────

    /** Equality based on database primary key. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "Category{id=" + id +
               ", name='" + name + "'" +
               ", departmentId=" + departmentId +
               ", active=" + isActive + "}";
    }
}
