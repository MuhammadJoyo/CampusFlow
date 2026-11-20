package com.campusflow.model.protocol;

import com.campusflow.util.JsonUtil;

import java.util.Objects;

/**
 * Protocol Data Transfer Object (DTO) for client-to-server requests.
 *
 * <h2>Protocol Specification (ARCH-001 §6.2)</h2>
 * <pre>
 * {
 *   "type": "MESSAGE_TYPE",
 *   "sessionId": "uuid-string-or-null-for-login",
 *   "payloadJson": "{ ... }"
 * }
 * </pre>
 *
 * <p>Messages are framed over TCP using newline delimiters ({@code \n}).
 * The {@code payloadJson} field holds a nested JSON object representing
 * the specific request parameters.</p>
 *
 * <p>SCD concepts: SCD-01 (Sockets), SCD-11 (DTO pattern).</p>
 */
public final class MessageRequest {

    // ── Fields ───────────────────────────────────────────────────────────────

    private final String type;
    private final String sessionId;
    private final String payloadJson;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Constructs a MessageRequest.
     *
     * @param type        action identifier (non-null, non-blank; e.g. "LOGIN", "SUBMIT_TICKET")
     * @param sessionId   active session token, or {@code null} for unauthenticated requests
     * @param payloadJson JSON string payload (may be null; defaults to "{}")
     * @throws NullPointerException     if type is null
     * @throws IllegalArgumentException if type is blank
     */
    public MessageRequest(String type, String sessionId, String payloadJson) {
        Objects.requireNonNull(type, "Request type must not be null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("Request type must not be blank");
        }
        this.type = type.trim();
        this.sessionId = (sessionId != null && !sessionId.isBlank()) ? sessionId.trim() : null;
        this.payloadJson = (payloadJson != null && !payloadJson.isBlank()) ? payloadJson.trim() : "{}";
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    /**
     * Creates an authenticated request with an arbitrary object serialized to JSON.
     *
     * @param type      request type string
     * @param sessionId session UUID
     * @param payload   payload object (serialized using {@link JsonUtil})
     * @return new MessageRequest
     */
    public static MessageRequest of(String type, String sessionId, Object payload) {
        return new MessageRequest(type, sessionId, JsonUtil.toJson(payload));
    }

    /**
     * Creates an unauthenticated request (e.g. for LOGIN).
     *
     * @param type    request type string
     * @param payload payload object (serialized using {@link JsonUtil})
     * @return new MessageRequest with null sessionId
     */
    public static MessageRequest unauthenticated(String type, Object payload) {
        return of(type, null, payload);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /** @return request message type identifier */
    public String getType() {
        return type;
    }

    /** @return session token UUID string, or {@code null} if unauthenticated */
    public String getSessionId() {
        return sessionId;
    }

    /** @return raw JSON payload string (never null, defaults to "{}") */
    public String getPayloadJson() {
        return payloadJson;
    }

    /**
     * Deserializes the {@code payloadJson} string into a typed object.
     *
     * @param clazz the expected payload class
     * @param <T>   payload type
     * @return parsed payload instance
     */
    public <T> T parsePayload(Class<T> clazz) {
        return JsonUtil.fromJson(payloadJson, clazz);
    }

    // ── Serialization ────────────────────────────────────────────────────────

    /**
     * Serializes this MessageRequest to a single-line JSON string.
     *
     * @return JSON string suitable for newline-delimited TCP transmission
     */
    public String toJson() {
        return JsonUtil.toJson(this);
    }

    /**
     * Deserializes a MessageRequest from a JSON string.
     *
     * @param rawJson JSON string
     * @return MessageRequest instance
     */
    public static MessageRequest fromJson(String rawJson) {
        return JsonUtil.fromJson(rawJson, MessageRequest.class);
    }

    // ── Object Overrides ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageRequest that)) return false;
        return Objects.equals(type, that.type) &&
               Objects.equals(sessionId, that.sessionId) &&
               Objects.equals(payloadJson, that.payloadJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, sessionId, payloadJson);
    }

    @Override
    public String toString() {
        return "MessageRequest{type='" + type + "', sessionId='" + sessionId + "'}";
    }
}
