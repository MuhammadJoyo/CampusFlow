package com.campusflow.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JsonUtil}.
 */
@DisplayName("JsonUtil")
class JsonUtilTest {

    record SampleDto(int id, String name, LocalDateTime timestamp) {}

    @Test
    @DisplayName("toJson serializes object to single line without raw newlines")
    void toJsonSingleLine() {
        SampleDto dto = new SampleDto(1, "Title with\nmultiple\nlines", LocalDateTime.of(2026, 11, 20, 10, 0));
        String json = JsonUtil.toJson(dto);

        assertNotNull(json);
        assertFalse(json.contains("\n"), "JSON string must not contain raw newline characters");
        assertFalse(json.contains("\r"), "JSON string must not contain raw carriage returns");
        assertTrue(json.contains("\\n"), "Newlines inside string literals must be escaped");
    }

    @Test
    @DisplayName("Roundtrip serialization of LocalDateTime preserves ISO format")
    void localDateTimeRoundtrip() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 20, 15, 30, 45);
        SampleDto original = new SampleDto(42, "TimeTest", now);

        String json = JsonUtil.toJson(original);
        SampleDto parsed = JsonUtil.fromJson(json, SampleDto.class);

        assertEquals(original.id(), parsed.id());
        assertEquals(original.name(), parsed.name());
        assertEquals(original.timestamp(), parsed.timestamp());
    }

    @Test
    @DisplayName("toJson(null) returns string 'null'")
    void toJsonNull() {
        assertEquals("null", JsonUtil.toJson(null));
    }

    @Test
    @DisplayName("fromJson throws IllegalArgumentException on null or blank input")
    void fromJsonNullOrBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.fromJson(null, SampleDto.class));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.fromJson("   ", SampleDto.class));
    }

    @Test
    @DisplayName("fromJson throws IllegalArgumentException on malformed JSON")
    void fromJsonMalformedThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.fromJson("{ not valid json :", SampleDto.class));
    }

    @Test
    @DisplayName("fromJson handles complex collection types")
    void genericCollections() {
        List<String> list = List.of("apple", "banana", "cherry");
        String json = JsonUtil.toJson(list);

        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<String>>(){}.getType();
        List<String> result = JsonUtil.fromJson(json, listType);

        assertEquals(3, result.size());
        assertEquals("banana", result.get(1));
    }
}
