package com.campusflow.model.protocol;

import com.campusflow.util.JsonUtil;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Protocol Data Transfer Object (DTO) for server-to-client responses.
 *
 * <h2>Protocol Specification (ARCH-001 §6.2)</h2>
 * <pre>
 * {
 *   "status": "OK | ERROR",
 *   "errorCode": "OPTIONAL_ERROR_CODE",
 *   "dataJson": "{ ... }"
 * }
 * </pre>
 *
 * <h2>Standard Error Codes</h2>
 * <ul>
 *   <li>{@code AUTH_FAILED} — invalid login credentials</li>
 *   <li>{@code SESSION_EXPIRED} — inactive session swept or invalid token</li>
 *   <li>{@code NOT_FOUND} — requested entity does not exist</li>
 *   <li>{@code FORBIDDEN} — role unauthorized for operation or student attempting to view others' tickets</li>
 *   <li>{@code VALIDATION_ERROR} — invalid request payload fields or state transition constraint</li>
 *   <li>{@code SERVER_ERROR} — unexpected internal exception</li>
 * </ul>
 *
 * <p>SCD concepts: SCD-01 (Sockets), SCD-11 (DTO pattern).</p>
 */
public final class MessageResponse {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    // ── Fields ───────────────────────────────────────────────────────────────

    private final String status;
    private final String errorCode;
    private final String dataJson;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Constructs a MessageResponse.
     *
     * @param status    "OK" or "ERROR" (non-null, non-blank)
     * @param errorCode error identifier string if status is ERROR, or null if OK
     * @param dataJson  JSON data or error message string (defaults to "{}")
     * @throws NullPointerException     if status is null
     * @throws IllegalArgumentException if status is blank
     */
    public MessageResponse(String status, String errorCode, String dataJson) {
        Objects.requireNonNull(status, "Response status must not be null");
        if (status.isBlank()) {
            throw new IllegalArgumentException("Response status must not be blank");
        }
        this.status = status.trim().toUpperCase();
        this.errorCode = (errorCode != null && !errorCode.isBlank()) ? errorCode.trim() : null;
        this.dataJson = (dataJson != null && !dataJson.isBlank()) ? dataJson.trim() : "{}";
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    /**
     * Creates an empty successful response (status "OK", no data).
     *
     * @return successful MessageResponse
     */
    public static MessageResponse ok() {
        return new MessageResponse(STATUS_OK, null, "{}");
    }

    /**
     * Creates a successful response with an arbitrary object serialized to JSON.
     *
     * @param data payload data object (serialized using {@link JsonUtil})
     * @return successful MessageResponse
     */
    public static MessageResponse ok(Object data) {
        return new MessageResponse(STATUS_OK, null, JsonUtil.toJson(data));
    }

    /**
     * Creates an error response with an error code and human-readable message.
     *
     * @param errorCode standard error code identifier (non-null)
     * @param message   descriptive message
     * @return error MessageResponse
     */
    public static MessageResponse error(String errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        String messagePayload = JsonUtil.toJson(message != null ? message : "An error occurred");
        return new MessageResponse(STATUS_ERROR, errorCode, messagePayload);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /** @return "OK" or "ERROR" */
    public String getStatus() {
        return status;
    }

    /** @return true if the response status is "OK" */
    public boolean isOk() {
        return STATUS_OK.equalsIgnoreCase(status);
    }

    /** @return error code string, or null on success */
    public String getErrorCode() {
        return errorCode;
    }

    /** @return JSON payload string (never null) */
    public String getDataJson() {
        return dataJson;
    }

    /**
     * Deserializes the {@code dataJson} into a typed object.
     *
     * @param clazz expected target class
     * @param <T>   expected type
     * @return parsed object
     */
    public <T> T parseData(Class<T> clazz) {
        return JsonUtil.fromJson(dataJson, clazz);
    }

    /**
     * Deserializes the {@code dataJson} into a generic type.
     *
     * @param typeOfT expected generic type
     * @param <T>     expected type
     * @return parsed object
     */
    public <T> T parseData(Type typeOfT) {
        return JsonUtil.fromJson(dataJson, typeOfT);
    }

    // ── Serialization ────────────────────────────────────────────────────────

    /**
     * Serializes this response to a single-line JSON string.
     *
     * @return JSON string suitable for newline-delimited TCP transmission
     */
    public String toJson() {
        return JsonUtil.toJson(this);
    }

    /**
     * Deserializes a MessageResponse from a JSON string.
     *
     * @param rawJson JSON string
     * @return MessageResponse instance
     */
    public static MessageResponse fromJson(String rawJson) {
        return JsonUtil.fromJson(rawJson, MessageResponse.class);
    }

    // ── Object Overrides ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageResponse that)) return false;
        return Objects.equals(status, that.status) &&
               Objects.equals(errorCode, that.errorCode) &&
               Objects.equals(dataJson, that.dataJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, errorCode, dataJson);
    }

    @Override
    public String toString() {
        return "MessageResponse{status='" + status + "', errorCode='" + errorCode + "'}";
    }
}
