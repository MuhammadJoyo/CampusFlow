package com.campusflow.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PasswordUtil}.
 *
 * Verifies BCrypt hashing and verification behaviour.
 * SCD concepts: SCD-11 (static utility), SCD-03 (security).
 */
@DisplayName("PasswordUtil")
class PasswordUtilTest {

    private static final String PLAINTEXT = "SecureP@ssw0rd!";

    @Test
    @DisplayName("Hash is not equal to plaintext")
    void hashNotEqualToPlaintext() {
        String hash = PasswordUtil.hash(PLAINTEXT);
        assertNotEquals(PLAINTEXT, hash);
    }

    @Test
    @DisplayName("BCrypt hash starts with $2a$12$ (correct format and cost)")
    void hashHasCorrectFormat() {
        String hash = PasswordUtil.hash(PLAINTEXT);
        assertTrue(hash.startsWith("$2a$12$"),
            "Expected BCrypt hash with cost 12, got: " + hash);
    }

    @Test
    @DisplayName("Correct plaintext verifies against its hash")
    void correctPasswordVerifies() {
        String hash = PasswordUtil.hash(PLAINTEXT);
        assertTrue(PasswordUtil.verify(PLAINTEXT, hash));
    }

    @Test
    @DisplayName("Wrong plaintext does not verify against hash")
    void wrongPasswordDoesNotVerify() {
        String hash = PasswordUtil.hash(PLAINTEXT);
        assertFalse(PasswordUtil.verify("WrongPassword!", hash));
    }

    @Test
    @DisplayName("Two hashes of same plaintext are different (random salt)")
    void twoHashesAreDifferent() {
        String hash1 = PasswordUtil.hash(PLAINTEXT);
        String hash2 = PasswordUtil.hash(PLAINTEXT);
        assertNotEquals(hash1, hash2, "BCrypt salts must be random — hashes must differ");
        // Both must still verify correctly
        assertTrue(PasswordUtil.verify(PLAINTEXT, hash1));
        assertTrue(PasswordUtil.verify(PLAINTEXT, hash2));
    }

    @Test
    @DisplayName("Null plaintext to hash() throws NullPointerException")
    void nullPlaintextThrows() {
        assertThrows(NullPointerException.class, () -> PasswordUtil.hash(null));
    }

    @Test
    @DisplayName("Blank plaintext to hash() throws IllegalArgumentException")
    void blankPlaintextThrows() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hash("   "));
    }

    @Test
    @DisplayName("Malformed hash string returns false (no exception)")
    void malformedHashReturnsFalse() {
        assertFalse(PasswordUtil.verify(PLAINTEXT, "not-a-bcrypt-hash"));
    }

    @Test
    @DisplayName("PasswordUtil cannot be instantiated (private constructor)")
    void cannotInstantiate() throws NoSuchMethodException {
        Constructor<PasswordUtil> ctor = PasswordUtil.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThrows(InvocationTargetException.class, ctor::newInstance);
    }
}
