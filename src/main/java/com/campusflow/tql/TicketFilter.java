package com.campusflow.tql;

import com.campusflow.model.enums.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates search and filter criteria for tickets.
 *
 * <h2>TQL & Query Generation (ADR-002, SCD-08, SCD-12)</h2>
 * <p>Constructed by the ANTLR visitor or UI filter panels. Responsible for producing
 * safe, parameterized SQL {@code WHERE} clauses enforcing server-side authorization.</p>
 *
 * <h2>Security & Authorization Scoping (ADR-002, NFR-07)</h2>
 * <ul>
 *   <li><strong>Student:</strong> Server forcefully injects {@code AND t.submitter_id = ?}
 *       bound to the authenticated user's ID. Student clients cannot view or search
 *       other students' complaints.</li>
 *   <li><strong>Staff:</strong> Can query within their department or assigned tickets.</li>
 *   <li><strong>Admin:</strong> Unrestricted global search.</li>
 * </ul>
 *
 * <p>All SQL output uses {@code ?} parameter placeholders with parameter lists to prevent
 * SQL injection.</p>
 */
public final class TicketFilter {

    /**
     * Immutable container for a parameterized SQL WHERE clause and its bound arguments.
     *
     * @param whereClause SQL clause string (e.g. "WHERE 1=1 AND t.status = ?")
     * @param parameters  list of parameter values to bind to the PreparedStatement
     */
    public record ParameterizedWhere(String whereClause, List<Object> parameters) {
        public ParameterizedWhere {
            Objects.requireNonNull(whereClause, "whereClause must not be null");
            parameters = (parameters != null) ? List.copyOf(parameters) : List.of();
        }
    }

    // ── Filter Fields ─────────────────────────────────────────────────────────

    private String status;
    private String priority;
    private String category;
    private String department;
    private String assignedUsername;
    private String submitterUsername;

    // ── Constructors ──────────────────────────────────────────────────────────

    public TicketFilter() {
    }

    public TicketFilter(String status,
                        String priority,
                        String category,
                        String department,
                        String assignedUsername,
                        String submitterUsername) {
        this.status = sanitize(status);
        this.priority = sanitize(priority);
        this.category = sanitize(category);
        this.department = sanitize(department);
        this.assignedUsername = sanitize(assignedUsername);
        this.submitterUsername = sanitize(submitterUsername);
    }

    // ── Predicates ───────────────────────────────────────────────────────────

    public boolean hasStatus()            { return status != null && !status.isBlank(); }
    public boolean hasPriority()          { return priority != null && !priority.isBlank(); }
    public boolean hasCategory()          { return category != null && !category.isBlank(); }
    public boolean hasDepartment()        { return department != null && !department.isBlank(); }
    public boolean hasAssignedUsername()  { return assignedUsername != null && !assignedUsername.isBlank(); }
    public boolean hasSubmitterUsername() { return submitterUsername != null && !submitterUsername.isBlank(); }

    // ── Accessors & Mutators ─────────────────────────────────────────────────

    public String getStatus()            { return status; }
    public String getPriority()          { return priority; }
    public String getCategory()          { return category; }
    public String getDepartment()        { return department; }
    public String getAssignedUsername()  { return assignedUsername; }
    public String getSubmitterUsername() { return submitterUsername; }

    public void setStatus(String status)                       { this.status = sanitize(status); }
    public void setPriority(String priority)                   { this.priority = sanitize(priority); }
    public void setCategory(String category)                   { this.category = sanitize(category); }
    public void setDepartment(String department)               { this.department = sanitize(department); }
    public void setAssignedUsername(String assignedUsername)   { this.assignedUsername = sanitize(assignedUsername); }
    public void setSubmitterUsername(String submitterUsername) { this.submitterUsername = sanitize(submitterUsername); }

    // ── SQL Clause Generation ────────────────────────────────────────────────

    /**
     * Legacy signature from UML class diagram. Delegates to {@link #buildWhereClause(Role, int)}
     * and returns the raw SQL clause string.
     *
     * @param role   authenticated user role
     * @param userId authenticated user database ID
     * @return SQL WHERE clause string
     */
    public String toSqlWhere(Role role, int userId) {
        return buildWhereClause(role, userId).whereClause();
    }

    /**
     * Builds a safe, parameterized SQL WHERE clause enforcing role-based scoping (ADR-002).
     *
     * <p>Assumes standard table aliases in repository queries:
     * {@code tickets t}, {@code categories c}, {@code departments d},
     * {@code users u_sub} (submitter), and {@code users u_assign} (assigned staff).</p>
     *
     * @param role   the authenticated user's role (never null)
     * @param userId the authenticated user's ID
     * @return ParameterizedWhere containing the parameterized SQL string and bound parameter values
     */
    public ParameterizedWhere buildWhereClause(Role role, int userId) {
        Objects.requireNonNull(role, "Role must not be null for server-side scoping");

        StringBuilder sql = new StringBuilder("WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // ── 1. Role-based Server Authorization Scoping (ADR-002) ──────────────
        if (role == Role.STUDENT) {
            // Forcefully scope to student's own tickets. Ignore any other submitterUsername filter.
            sql.append(" AND t.submitter_id = ?");
            params.add(userId);
        } else if (role == Role.STAFF) {
            // Staff search defaults to their department or tickets assigned to them if submitter not queried
            if (hasSubmitterUsername()) {
                sql.append(" AND u_sub.username = ?");
                params.add(submitterUsername);
            }
        } else if (role == Role.ADMIN) {
            // Admin has global visibility
            if (hasSubmitterUsername()) {
                sql.append(" AND u_sub.username = ?");
                params.add(submitterUsername);
            }
        }

        // ── 2. Common Filter Criteria ─────────────────────────────────────────
        if (hasStatus()) {
            sql.append(" AND UPPER(t.status) = UPPER(?)");
            params.add(status);
        }

        if (hasPriority()) {
            sql.append(" AND UPPER(t.priority) = UPPER(?)");
            params.add(priority);
        }

        if (hasCategory()) {
            sql.append(" AND UPPER(c.name) = UPPER(?)");
            params.add(category);
        }

        if (hasDepartment()) {
            sql.append(" AND UPPER(d.name) = UPPER(?)");
            params.add(department);
        }

        if (hasAssignedUsername()) {
            sql.append(" AND u_assign.username = ?");
            params.add(assignedUsername);
        }

        return new ParameterizedWhere(sql.toString(), params);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim();
    }

    // ── Object Overrides ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TicketFilter that)) return false;
        return Objects.equals(status, that.status) &&
               Objects.equals(priority, that.priority) &&
               Objects.equals(category, that.category) &&
               Objects.equals(department, that.department) &&
               Objects.equals(assignedUsername, that.assignedUsername) &&
               Objects.equals(submitterUsername, that.submitterUsername);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, priority, category, department, assignedUsername, submitterUsername);
    }

    @Override
    public String toString() {
        return "TicketFilter{" +
               "status='" + status + '\'' +
               ", priority='" + priority + '\'' +
               ", category='" + category + '\'' +
               ", department='" + department + '\'' +
               ", assignedUsername='" + assignedUsername + '\'' +
               ", submitterUsername='" + submitterUsername + '\'' +
               '}';
    }
}
