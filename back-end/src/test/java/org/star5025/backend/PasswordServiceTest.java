package org.star5025.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.star5025.backend.service.PasswordService;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {
    private final PasswordService passwords = new PasswordService();
    private static final String LEGACY = "e10adc3949ba59abbe56e057f20f883e";

    @Test
    void bcryptUsesSaltAndMatchesOnlyCorrectPassword() {
        String first = passwords.encode("123456");
        String second = passwords.encode("123456");
        assertTrue(first.startsWith("$2a$12$"));
        assertNotEquals(first, second);
        assertTrue(passwords.matches("123456", first));
        assertFalse(passwords.matches("wrong123", first));
        assertFalse(passwords.isLegacyMd5(first));
    }

    @Test
    void legacyMd5VerificationIsStrictAndThreadSafe() {
        assertTrue(passwords.isLegacyMd5(LEGACY));
        assertTrue(passwords.matches("123456", LEGACY));
        assertTrue(passwords.matches("123456", LEGACY.toUpperCase()));
        assertFalse(passwords.matches("wrong123", LEGACY));
        assertTrue(IntStream.range(0, 200).parallel().allMatch(i -> passwords.matches("123456", LEGACY)));
    }

    @Test
    void neverFallsBackToPlaintextOrUnknownEncoding() {
        for (String hash : new String[]{"123456", "{noop}123456", "{md5}" + LEGACY, "$2a$broken", ""}) {
            assertFalse(passwords.matches("123456", hash));
        }
        assertFalse(passwords.matches(null, LEGACY));
        assertFalse(passwords.matches("123456", null));
    }

    @Test
    void legacyMigrationPreservesShortAndLongPasswordsWithoutTruncation() throws Exception {
        for (String raw : new String[]{"abc", "a".repeat(73), "密".repeat(25)}) {
            String md5 = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertTrue(passwords.matches(raw, md5));
            String migrated = passwords.encodeLegacy(raw);
            assertFalse(passwords.isLegacyMd5(migrated));
            assertTrue(passwords.matches(raw, migrated));
            assertFalse(passwords.matches(raw + "x", migrated));
            if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                assertTrue(migrated.startsWith("{bcrypt-sha256}$2a$12$"));
                assertFalse(passwords.matches(raw.substring(0, 20), migrated));
            }
        }
        assertFalse(passwords.matches("123456", "{bcrypt-sha256}broken"));
    }

    @Test
    void rejectsInvalidOrTruncatedNewPasswords() {
        for (String raw : new String[]{"", "    ", "1234", "a".repeat(73), "密".repeat(25)}) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> passwords.encode(raw));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        }
        assertThrows(ResponseStatusException.class, () -> passwords.encode(null));
        String hash = passwords.encode("a".repeat(72));
        assertTrue(passwords.matches("a".repeat(72), hash));
        assertFalse(passwords.matches("a".repeat(73), hash));
    }
}
