package com.campusflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuditLog}.
 */
@DisplayName("AuditLog entity")
class AuditLogTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2024, 3, 15, 14, 30);

    @Test
    @DisplayName("AuditLog fields are accessible via getters")
    void fieldsAccessible() {
        AuditLog log = new AuditLog(1, 10, "TICKET_CREATED", "TICKET", 42, NOW, "Ticket submitted by student");
        assertEquals(1,                log.getId());
        assertEquals(10,               log.getActorId());
        assertEquals("TICKET_CREATED", log.getAction());
        assertEquals("TICKET",         log.getEntityType());
        assertEquals(42,               log.getEntityId());
        assertEquals(NOW,              log.getTimestamp());
        assertEquals("Ticket submitted by student", log.getDetails());
    }

    @Test
    @DisplayName("Null action is rejected")
    void nullActionRejected() {
        assertThrows(NullPointerException.class, () ->
            new AuditLog(1, 10, null, "TICKET", 42, NOW, "details"));
    }

    @Test
    @DisplayName("Blank entityType is rejected")
    void blankEntityTypeRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new AuditLog(1, 10, "ACTION", "  ", 42, NOW, "details"));
    }

    @Test
    @DisplayName("Null timestamp is rejected")
    void nullTimestampRejected() {
        assertThrows(NullPointerException.class, () ->
            new AuditLog(1, 10, "ACTION", "TICKET", 42, null, "details"));
    }

    @Test
    @DisplayName("AuditLog has no setters (immutable)")
    void noSettersDeclared() {
        var methods = java.util.Arrays.stream(AuditLog.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.startsWith("set"))
                .toList();
        assertTrue(methods.isEmpty(), "AuditLog must have no setters, found: " + methods);
    }
}
