package io.emmansun.lightcrypto.service;

import io.emmansun.lightcrypto.exception.CryptoException;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.digests.SHA256Digest;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Core crypto engine — AES-256-GCM encryption/decryption + HMAC-SHA-256 blind index
 * + KCV/Binding computation.
 */
public class CryptoCodec {

    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int ZERO_BLOCK_SIZE = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypt plaintext and return IV || ciphertext.
     */
    public byte[] encrypt(byte[] dek, byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(dek, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] result = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    /**
     * Decrypt IV || ciphertext and return the plaintext.
     */
    public byte[] decrypt(byte[] dek, byte[] data) {
        try {
            byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(data, IV_LENGTH, data.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(dek, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new CryptoException("Decryption failed", e);
        }
    }

    /**
     * Generate a blind index: HMAC-SHA-256(hmacKey, fieldName + ":" + serializedValue).
     * Accepts byte[] for the serialized value and outputs base64url without padding.
     */
    public String generateBlindIndex(byte[] hmacKey, String fieldName, byte[] serializedValue) {
        byte[] fieldNameBytes = fieldName.getBytes(StandardCharsets.UTF_8);
        // Build HMAC input: fieldNameBytes + ":" + serializedValue
        byte[] input = new byte[fieldNameBytes.length + 1 + serializedValue.length];
        System.arraycopy(fieldNameBytes, 0, input, 0, fieldNameBytes.length);
        input[fieldNameBytes.length] = 0x3A; // colon separator
        System.arraycopy(serializedValue, 0, input, fieldNameBytes.length + 1, serializedValue.length);

        HMac hmac = new HMac(new SHA256Digest());
        hmac.init(new KeyParameter(hmacKey));
        hmac.update(input, 0, input.length);

        byte[] result = new byte[hmac.getMacSize()];
        hmac.doFinal(result, 0);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
    }

    /**
     * Compute the KCV (Key Check Value): encrypts an all-zero block with AES-256-GCM
     * and returns the Hex of IV+ciphertext. Uses a fixed all-zero IV for determinism.
     */
    public String computeKcv(byte[] key) {
        try {
            byte[] zeroIv = new byte[IV_LENGTH]; // All-zero IV for determinism
            byte[] zeroBlock = new byte[ZERO_BLOCK_SIZE];

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, zeroIv));

            byte[] encrypted = cipher.doFinal(zeroBlock);
            return HexFormat.of().formatHex(encrypted);
        } catch (Exception e) {
            throw new CryptoException("KCV computation failed", e);
        }
    }

    /**
     * Compute a dual-key binding fingerprint: HMAC-SHA-256(hmacKey, aesKey).
     */
    public String computeBinding(byte[] hmacKey, byte[] aesKey) {
        HMac hmac = new HMac(new SHA256Digest());
        hmac.init(new KeyParameter(hmacKey));
        hmac.update(aesKey, 0, aesKey.length);

        byte[] result = new byte[hmac.getMacSize()];
        hmac.doFinal(result, 0);

        return HexFormat.of().formatHex(result);
    }
}
