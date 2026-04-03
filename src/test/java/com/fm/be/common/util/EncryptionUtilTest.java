package com.fm.be.common.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EncryptionUtil}.
 */
class EncryptionUtilTest {

    private static final String TEST_KEY_BASE64 =
            Base64.getEncoder().encodeToString(new byte[32]);

    private EncryptionUtil createUtil(String key) {
        return new EncryptionUtil(key);
    }

    @Test
    void encrypt_decrypt_roundtrip() {
        EncryptionUtil util = createUtil(TEST_KEY_BASE64);
        String plaintext = "123456789012";
        byte[] encrypted = util.encrypt(plaintext);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 12);
        String decrypted = util.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_produces_different_ciphertexts_for_same_input() {
        EncryptionUtil util = createUtil(TEST_KEY_BASE64);
        byte[] a = util.encrypt("ABCDE1234F");
        byte[] b = util.encrypt("ABCDE1234F");
        assertFalse(java.util.Arrays.equals(a, b),
                "Each encryption should use a unique IV");
    }

    @Test
    void encrypt_returns_null_when_disabled() {
        EncryptionUtil util = createUtil(null);
        assertNull(util.encrypt("test"));
        assertFalse(util.isEnabled());
    }

    @Test
    void encrypt_returns_null_for_null_input() {
        EncryptionUtil util = createUtil(TEST_KEY_BASE64);
        assertNull(util.encrypt(null));
    }

    @Test
    void decrypt_returns_null_when_disabled() {
        EncryptionUtil util = createUtil(null);
        assertNull(util.decrypt(new byte[]{1, 2, 3}));
    }

    @Test
    void decrypt_returns_null_for_null_input() {
        EncryptionUtil util = createUtil(TEST_KEY_BASE64);
        assertNull(util.decrypt(null));
    }

    @Test
    void decrypt_with_wrong_key_throws() {
        EncryptionUtil encryptor = createUtil(TEST_KEY_BASE64);
        byte[] encrypted = encryptor.encrypt("sensitive-data");

        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        EncryptionUtil wrongDecryptor = createUtil(
                Base64.getEncoder().encodeToString(otherKey));

        assertThrows(IllegalStateException.class,
                () -> wrongDecryptor.decrypt(encrypted));
    }

    @Test
    void isEnabled_returns_true_with_valid_key() {
        EncryptionUtil util = createUtil(TEST_KEY_BASE64);
        assertTrue(util.isEnabled());
    }

    @Test
    void isEnabled_returns_false_with_blank_key() {
        EncryptionUtil util = createUtil("  ");
        assertFalse(util.isEnabled());
    }
}
