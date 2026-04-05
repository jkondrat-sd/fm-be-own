package com.fm.be.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption utility for PII fields (Aadhaar, PAN).
 *
 * <p>Encrypted values are stored as bytes in PostgreSQL BYTEA columns.
 * The format is: [12-byte IV][ciphertext+tag]. The IV is randomly
 * generated per encryption call to ensure uniqueness.</p>
 *
 * <p>The encryption key is sourced from the environment variable
 * {@code PII_ENCRYPTION_KEY} and must be a 32-byte Base64-encoded string.</p>
 */
@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionUtil(
            @Value("${app.encryption.pii-key:#{null}}") String keyString) {
        if (keyString != null && !keyString.isBlank()) {
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(keyString);
            } catch (IllegalArgumentException e) {
                // For local dev convenience: if not valid base64, check if it's exactly a 32-byte raw string
                keyBytes = keyString.getBytes(StandardCharsets.UTF_8);
                if (keyBytes.length != 32) {
                    throw new IllegalArgumentException(
                            "PII key must be a 32-byte Base64 string or a raw 32-byte string, but got invalid format/length.", e);
                }
            }
            this.keySpec = new SecretKeySpec(keyBytes, "AES");
        } else {
            this.keySpec = null;
        }
    }

    /**
     * Encrypts a plaintext string to bytes using AES-256-GCM.
     *
     * @param plaintext the PII value to encrypt
     * @return encrypted bytes (IV + ciphertext), or null if encryption is disabled
     */
    public byte[] encrypt(String plaintext) {
        if (keySpec == null || plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(
                    GCM_IV_LENGTH + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            throw new IllegalStateException("PII encryption failed", e);
        }
    }

    /**
     * Decrypts bytes back to the original plaintext string.
     *
     * @param encrypted the encrypted bytes (IV + ciphertext)
     * @return the decrypted plaintext, or null if input is null or encryption disabled
     */
    public String decrypt(byte[] encrypted) {
        if (keySpec == null || encrypted == null) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainBytes = cipher.doFinal(ciphertext);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("PII decryption failed", e);
        }
    }

    /**
     * Checks whether PII encryption is configured and available.
     *
     * @return true if the encryption key is set
     */
    public boolean isEnabled() {
        return keySpec != null;
    }
}
