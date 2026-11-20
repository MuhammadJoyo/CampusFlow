package com.campusflow.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Centralized utility for JSON serialization and deserialization using Gson.
 *
 * <h2>Encapsulation Principle</h2>
 * <p>Domain entities, DTOs, and network handlers interact exclusively with
 * this utility and never directly import {@code com.google.gson.*}. This preserves
 * architectural decoupling and modularity.</p>
 *
 * <h2>Protocol Framing (ADR-006)</h2>
 * <p>Socket communication uses newline-delimited JSON. {@code JsonUtil} produces
 * compact JSON without pretty-printing, ensuring that string literals with newlines
 * are safely escaped as {@code \n} characters so that a message always occupies
 * exactly one physical line.</p>
 *
 * <p>SCD concepts: SCD-11 (static utility, encapsulation).</p>
 */
public final class JsonUtil {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .disableHtmlEscaping()
            .create();

    private JsonUtil() {
        throw new UnsupportedOperationException("JsonUtil is a static utility class");
    }

    /**
     * Serializes an object to its JSON string representation.
     *
     * @param obj the object to serialize (may be null)
     * @return compact single-line JSON string
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        return GSON.toJson(obj);
    }

    /**
     * Deserializes a JSON string into an instance of the specified class.
     *
     * @param json  the JSON string to parse (non-null, non-blank)
     * @param clazz the target class
     * @param <T>   the expected type
     * @return deserialized object
     * @throws IllegalArgumentException if json is null, blank, or invalid JSON syntax
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        Objects.requireNonNull(clazz, "Target class must not be null");
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON string must not be null or blank");
        }
        try {
            return GSON.fromJson(json, clazz);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes a JSON string into a complex generic type.
     *
     * @param json    the JSON string to parse (non-null, non-blank)
     * @param typeOfT the generic type token
     * @param <T>     the expected type
     * @return deserialized object
     * @throws IllegalArgumentException if json is null, blank, or invalid JSON syntax
     */
    public static <T> T fromJson(String json, Type typeOfT) {
        Objects.requireNonNull(typeOfT, "Target type must not be null");
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON string must not be null or blank");
        }
        try {
            return GSON.fromJson(json, typeOfT);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    // ── Custom Type Adapter for LocalDateTime ─────────────────────────────────

    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(ISO_FORMATTER));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.parse(in.nextString(), ISO_FORMATTER);
        }
    }
}
