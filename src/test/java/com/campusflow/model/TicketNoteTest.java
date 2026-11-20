package com.campusflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TicketNote}.
 *
 * Verifies entity-level validation (non-null, non-blank) and confirms that
 * the 10-character minimum is NOT enforced at this layer (it belongs to
 * TicketService — see compliance review §4).
 */
@DisplayName("TicketNote entity")
class TicketNoteTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 10, 0);

    @Test
    @DisplayName("Valid note is created successfully")
    void validNoteCreated() {
        TicketNote note = new TicketNote(1, 1, 1, "This is a valid note.", NOW);
        assertEquals("This is a valid note.", note.getNote());
        assertEquals(1, note.getTicketId());
        assertEquals(1, note.getAuthorId());
    }

    @Test
    @DisplayName("One-character note is accepted (entity has no length minimum)")
    void singleCharNoteAccepted() {
        // The 10-char minimum belongs to TicketService, not to this entity
        assertDoesNotThrow(() -> new TicketNote(1, 1, 1, "X", NOW));
    }

    @Test
    @DisplayName("Null note is rejected")
    void nullNoteRejected() {
        assertThrows(NullPointerException.class, () ->
            new TicketNote(1, 1, 1, null, NOW));
    }

    @Test
    @DisplayName("Blank note is rejected")
    void blankNoteRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new TicketNote(1, 1, 1, "   ", NOW));
    }

    @Test
    @DisplayName("ticketId <= 0 is rejected")
    void invalidTicketIdRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new TicketNote(1, 0, 1, "Valid note text", NOW));
    }

    @Test
    @DisplayName("authorId <= 0 is rejected")
    void invalidAuthorIdRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new TicketNote(1, 1, -1, "Valid note text", NOW));
    }

    @Test
    @DisplayName("getNote returns exact text")
    void getNoteReturnsExactText() {
        String text = "Printer on floor 3 is jammed.";
        TicketNote note = new TicketNote(0, 2, 5, text, NOW);
        assertEquals(text, note.getNote());
    }
}
