package com.campusflow.model.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MessageRequest}.
 */
@DisplayName("MessageRequest Protocol DTO")
class MessageRequestTest {

    record LoginPayload(String username, String password) {}

    @Test
    @DisplayName("Creates valid authenticated MessageRequest")
    void validAuthenticatedRequest() {
        MessageRequest req = new MessageRequest("SUBMIT_TICKET", "session-123", "{\"title\":\"Printer broken\"}");

        assertEquals("SUBMIT_TICKET", req.getType());
        assertEquals("session-123", req.getSessionId());
        assertEquals("{\"title\":\"Printer broken\"}", req.getPayloadJson());
    }

    @Test
    @DisplayName("Creates valid unauthenticated MessageRequest with null sessionId")
    void validUnauthenticatedRequest() {
        LoginPayload payload = new LoginPayload("student1", "secret");
        MessageRequest req = MessageRequest.unauthenticated("LOGIN", payload);

        assertEquals("LOGIN", req.getType());
        assertNull(req.getSessionId());
        assertTrue(req.getPayloadJson().contains("student1"));
    }

    @Test
    @DisplayName("Rejects null or blank request type")
    void rejectsInvalidType() {
        assertThrows(NullPointerException.class, () -> new MessageRequest(null, "s-1", "{}"));
        assertThrows(IllegalArgumentException.class, () -> new MessageRequest("  ", "s-1", "{}"));
    }

    @Test
    @DisplayName("Defaults null or blank payloadJson to '{}'")
    void defaultsEmptyPayload() {
        MessageRequest req = new MessageRequest("LOGOUT", "s-1", null);
        assertEquals("{}", req.getPayloadJson());

        MessageRequest req2 = new MessageRequest("LOGOUT", "s-1", "   ");
        assertEquals("{}", req2.getPayloadJson());
    }

    @Test
    @DisplayName("parsePayload extracts typed payload correctly")
    void parsePayloadTyped() {
        MessageRequest req = MessageRequest.unauthenticated("LOGIN", new LoginPayload("john_doe", "pass123"));
        LoginPayload parsed = req.parsePayload(LoginPayload.class);

        assertEquals("john_doe", parsed.username());
        assertEquals("pass123", parsed.password());
    }

    @Test
    @DisplayName("Roundtrip toJson and fromJson preserves all fields")
    void roundtripSerialization() {
        MessageRequest original = new MessageRequest("SEARCH_TICKETS", "session-uuid-456", "{\"priority\":\"HIGH\"}");
        String json = original.toJson();

        MessageRequest deserialized = MessageRequest.fromJson(json);
        assertEquals(original, deserialized);
        assertEquals(original.hashCode(), deserialized.hashCode());
    }

    @Test
    @DisplayName("toString provides useful summary without breaking")
    void toStringContainsTypeAndSession() {
        MessageRequest req = new MessageRequest("PING", "sess-99", "{}");
        assertTrue(req.toString().contains("PING"));
        assertTrue(req.toString().contains("sess-99"));
    }
}
