package com.campusflow.model.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MessageResponse}.
 */
@DisplayName("MessageResponse Protocol DTO")
class MessageResponseTest {

    record TicketConfirmation(int ticketId, String status) {}

    @Test
    @DisplayName("ok() creates successful response with default payload")
    void okEmpty() {
        MessageResponse resp = MessageResponse.ok();

        assertTrue(resp.isOk());
        assertEquals("OK", resp.getStatus());
        assertNull(resp.getErrorCode());
        assertEquals("{}", resp.getDataJson());
    }

    @Test
    @DisplayName("ok(data) serializes data object correctly")
    void okWithData() {
        TicketConfirmation conf = new TicketConfirmation(101, "OPEN");
        MessageResponse resp = MessageResponse.ok(conf);

        assertTrue(resp.isOk());
        assertNull(resp.getErrorCode());
        assertTrue(resp.getDataJson().contains("101"));

        TicketConfirmation parsed = resp.parseData(TicketConfirmation.class);
        assertEquals(101, parsed.ticketId());
        assertEquals("OPEN", parsed.status());
    }

    @Test
    @DisplayName("error(code, msg) creates error response")
    void errorResponse() {
        MessageResponse resp = MessageResponse.error("AUTH_FAILED", "Invalid username or password");

        assertFalse(resp.isOk());
        assertEquals("ERROR", resp.getStatus());
        assertEquals("AUTH_FAILED", resp.getErrorCode());
        assertTrue(resp.getDataJson().contains("Invalid username or password"));
    }

    @Test
    @DisplayName("Rejects null or blank status")
    void rejectsInvalidStatus() {
        assertThrows(NullPointerException.class, () -> new MessageResponse(null, null, "{}"));
        assertThrows(IllegalArgumentException.class, () -> new MessageResponse("   ", null, "{}"));
    }

    @Test
    @DisplayName("parseData handles generic collection types")
    void parseDataGeneric() {
        List<String> items = List.of("A", "B", "C");
        MessageResponse resp = MessageResponse.ok(items);

        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<String>>(){}.getType();
        List<String> parsed = resp.parseData(listType);

        assertEquals(3, parsed.size());
        assertEquals("B", parsed.get(1));
    }

    @Test
    @DisplayName("Roundtrip toJson and fromJson preserves all fields")
    void roundtripSerialization() {
        MessageResponse original = MessageResponse.error("SESSION_EXPIRED", "Session timed out after 30 minutes");
        String json = original.toJson();

        MessageResponse deserialized = MessageResponse.fromJson(json);
        assertEquals(original, deserialized);
        assertEquals(original.hashCode(), deserialized.hashCode());
    }
}
