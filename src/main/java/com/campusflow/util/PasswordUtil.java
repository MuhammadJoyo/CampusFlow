package com.campusflow.util;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Objects;

/**
 * Static utility for BCrypt password hashing and verification.
 *
 * <p>All CampusFlow passwords are stored as BCrypt hashes; plaintext passwords
 * are never persisted or logged (SRS NFR-07, ADR-004).</p>
 *
 * <h2>API</h2>
 * <ul>
 *   <li>{@link #hash(String)} — produces a salted BCrypt hash using cost factor 12.</li>
 *   <li>{@link #verify(String, String)} — safely verifies a plaintext against a stored hash.</li>
 * </ul>
 *
 * <p>This class cannot be instantiated.</p>
 *
 * <p>SCD concepts: SCD-11 (static utility — no-instance idiom), SCD-03 (security).</p>
 */
public final class PasswordUtil {

    // ── BCrypt Configuration ─────────────────────────────────────────────────

    /**
     * BCrypt work factor (log-rounds). Cost 12 ≈ 300 ms per hash on modern hardware,
     * which is acceptable for a login operation and significantly resistant to brute-force.
     */
    private static final int BCRYPT_COST = 12;

    // ── No-instance idiom ────────────────────────────────────────────────────

    /** Prevents instantiation. */
    private PasswordUtil() {
        throw new UnsupportedOperationException("PasswordUtil is a static utility class");
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Hashes a plaintext password using BCrypt with a randomly generated salt
     * and cost factor {@value #BCRYPT_COST}.
     *
     * <p>Two calls with the same plaintext will produce different hashes due to
     * random salt generation — this is expected and correct behaviour.</p>
     *
     * @param plaintext the plaintext password to hash (non-null, non-blank)
     * @return a BCrypt hash string in the form {@code $2a$12$...}
     * @throws NullPointerException     if plaintext is null
     * @throws IllegalArgumentException if plaintext is blank
     */
    public static String hash(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        if (plaintext.isBlank()) throw new IllegalArgumentException("plaintext must not be blank");
        return BCrypt.hashpw(plaintext, BCrypt.gensalt(BCRYPT_COST));
    }

    /**
     * Verifies a plaintext password against a stored BCrypt hash.
     *
     * <p>Returns {@code false} for any verification failure, including malformed
     * hash strings, rather than propagating exceptions to the caller.</p>
     *
     * @param plaintext the plaintext password supplied by the user (non-null)
     * @param hash      the stored BCrypt hash (non-null)
     * @return {@code true} if the plaintext matches the hash; {@code false} otherwise
     * @throws NullPointerException if plaintext or hash is null
     */
    public static boolean verify(String plaintext, String hash) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        Objects.requireNonNull(hash,      "hash must not be null");
        try {
            return BCrypt.checkpw(plaintext, hash);
        } catch (IllegalArgumentException e) {
            // BCrypt throws if the hash string is malformed — treat as mismatch
            return false;
        }
    }
}
