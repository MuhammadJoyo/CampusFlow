package com.campusflow.tql;

import com.campusflow.model.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TicketFilter}.
 *
 * Verifies filter predicate checks, parameterized SQL WHERE clause building,
 * role-based server-side scoping (ADR-002), and SQL injection prevention.
 */
@DisplayName("TicketFilter")
class TicketFilterTest {

    @Test
    @DisplayName("Empty filter for Admin produces base query without extra conditions")
    void emptyFilterAdmin() {
        TicketFilter filter = new TicketFilter();
        TicketFilter.ParameterizedWhere result = filter.buildWhereClause(Role.ADMIN, 1);

        assertEquals("WHERE 1=1", result.whereClause());
        assertTrue(result.parameters().isEmpty());
    }

    @Test
    @DisplayName("Student role forcefully injects submitter_id constraint (ADR-002)")
    void studentScopingEnforced() {
        TicketFilter filter = new TicketFilter();
        TicketFilter.ParameterizedWhere result = filter.buildWhereClause(Role.STUDENT, 42);

        assertTrue(result.whereClause().contains("AND t.submitter_id = ?"));
        assertEquals(1, result.parameters().size());
        assertEquals(42, result.parameters().get(0));
    }

    @Test
    @DisplayName("Student cannot override submitter_id by providing submitterUsername filter")
    void studentCannotOverrideSubmitter() {
        TicketFilter filter = new TicketFilter();
        filter.setSubmitterUsername("victim_student");

        TicketFilter.ParameterizedWhere result = filter.buildWhereClause(Role.STUDENT, 10);

        assertTrue(result.whereClause().contains("AND t.submitter_id = ?"));
        assertFalse(result.whereClause().contains("u_sub.username = ?"),
            "Student query must not permit querying arbitrary submitter usernames");
        assertEquals(1, result.parameters().size());
        assertEquals(10, result.parameters().get(0));
    }

    @Test
    @DisplayName("Staff and Admin can query by submitterUsername")
    void adminCanFilterBySubmitter() {
        TicketFilter filter = new TicketFilter();
        filter.setSubmitterUsername("john_student");

        TicketFilter.ParameterizedWhere adminResult = filter.buildWhereClause(Role.ADMIN, 1);
        assertTrue(adminResult.whereClause().contains("AND u_sub.username = ?"));
        assertEquals("john_student", adminResult.parameters().get(0));

        TicketFilter.ParameterizedWhere staffResult = filter.buildWhereClause(Role.STAFF, 2);
        assertTrue(staffResult.whereClause().contains("AND u_sub.username = ?"));
        assertEquals("john_student", staffResult.parameters().get(0));
    }

    @Test
    @DisplayName("Multi-field filter builds correct parameterized SQL and parameter list")
    void multiFieldFilter() {
        TicketFilter filter = new TicketFilter();
        filter.setStatus("OPEN");
        filter.setPriority("CRITICAL");
        filter.setDepartment("IT");
        filter.setCategory("Network Issue");
        filter.setAssignedUsername("tech_bob");

        TicketFilter.ParameterizedWhere result = filter.buildWhereClause(Role.ADMIN, 1);

        String sql = result.whereClause();
        assertTrue(sql.contains("AND UPPER(t.status) = UPPER(?)"));
        assertTrue(sql.contains("AND UPPER(t.priority) = UPPER(?)"));
        assertTrue(sql.contains("AND UPPER(d.name) = UPPER(?)"));
        assertTrue(sql.contains("AND UPPER(c.name) = UPPER(?)"));
        assertTrue(sql.contains("AND u_assign.username = ?"));

        assertEquals(5, result.parameters().size());
        assertEquals("OPEN", result.parameters().get(0));
        assertEquals("CRITICAL", result.parameters().get(1));
        assertEquals("Network Issue", result.parameters().get(2));
        assertEquals("IT", result.parameters().get(3));
        assertEquals("tech_bob", result.parameters().get(4));
    }

    @Test
    @DisplayName("SQL Injection attempts are neutralized via parameterization")
    void sqlInjectionNeutralized() {
        TicketFilter filter = new TicketFilter();
        String injectionAttempt = "OPEN' OR '1'='1'; DROP TABLE tickets; --";
        filter.setStatus(injectionAttempt);

        TicketFilter.ParameterizedWhere result = filter.buildWhereClause(Role.ADMIN, 1);

        // SQL clause must contain '?' placeholder and NOT the literal SQL injection code
        assertEquals("WHERE 1=1 AND UPPER(t.status) = UPPER(?)", result.whereClause());
        assertFalse(result.whereClause().contains("DROP TABLE"));
        assertFalse(result.whereClause().contains("OR '1'='1'"));

        // Malicious input is safely treated as a single literal parameter string
        assertEquals(1, result.parameters().size());
        assertEquals(injectionAttempt, result.parameters().get(0));
    }

    @Test
    @DisplayName("toSqlWhere legacy method returns valid SQL string")
    void toSqlWhereLegacyMethod() {
        TicketFilter filter = new TicketFilter();
        filter.setStatus("RESOLVED");

        String sql = filter.toSqlWhere(Role.STUDENT, 7);
        assertTrue(sql.startsWith("WHERE 1=1"));
        assertTrue(sql.contains("t.submitter_id = ?"));
        assertTrue(sql.contains("UPPER(t.status) = UPPER(?)"));
    }

    @Test
    @DisplayName("Filter predicates accurately reflect set fields")
    void filterPredicates() {
        TicketFilter filter = new TicketFilter();
        assertFalse(filter.hasStatus());
        assertFalse(filter.hasPriority());

        filter.setStatus("IN_PROGRESS");
        filter.setPriority("HIGH");
        assertTrue(filter.hasStatus());
        assertTrue(filter.hasPriority());

        filter.setStatus("   ");
        assertFalse(filter.hasStatus(), "Blank status should be treated as null/unset");
    }
}
