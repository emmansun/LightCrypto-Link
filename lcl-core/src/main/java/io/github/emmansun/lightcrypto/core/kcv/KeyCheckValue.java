package io.github.emmansun.lightcrypto.core.kcv;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.exception.CryptoException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

/**
 * Key Check Value (KCV) and binding hash utilities.
 *
 * <p>KCV provides a deterministic fingerprint of a key without revealing the key material.
 * Used to verify key integrity after unwrap operations.
 *
 * @since 1.0.0
 */
public final class KeyCheckValue {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final byte[] HMAC_KCV_MESSAGE = "lcl-kcv-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private KeyCheckValue() {
    }

    /**
     * Computes the KCV for a symmetric encryption key (DEK).
     * Delegates to the algorithm-specific encryptor's KCV computation
     * (encrypts a zero block with zero IV, returns hex of result).
     *
     * @param key       the encryption key
     * @param algorithm the algorithm the key is used with
     * @return lowercase hex string of the KCV
     */
    public static String computeDekKcv(byte[] key, AlgorithmId algorithm) {
        return CryptoCodec.getEncryptor(algorithm).computeKcv(key);
    }

    /**
     * Computes the KCV for an HMAC key.
     * Uses HMAC-SHA-256 with a fixed message "lcl-kcv-v1" and returns the full hex output.
     *
     * @param hmacKey the HMAC key
     * @return lowercase hex string of the KCV
     */
    public static String computeHmacKcv(byte[] hmacKey) {
        try {
            Mac hmac = Mac.getInstance(HMAC_SHA256);
            hmac.init(new SecretKeySpec(hmacKey, HMAC_SHA256));
            byte[] result = hmac.doFinal(HMAC_KCV_MESSAGE);
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new CryptoException("HMAC KCV computation failed", e);
        }
    }

    /**
     * Computes a dual-key binding fingerprint: HMAC-SHA-256(hmacKey, dek).
     * This binds the DEK and HMAC key pair together, detecting if either is swapped.
     *
     * @param hmacKey the HMAC key
     * @param dek     the data encryption key
     * @return lowercase hex string of the binding hash
     */
    public static String computeBinding(byte[] hmacKey, byte[] dek) {
        try {
            Mac hmac = Mac.getInstance(HMAC_SHA256);
            hmac.init(new SecretKeySpec(hmacKey, HMAC_SHA256));
            byte[] result = hmac.doFinal(dek);
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new CryptoException("Binding hash computation failed", e);
        }
    }

    /**
     * Verifies a DEK KCV against an expected value.
     *
     * @param key       the encryption key
     * @param algorithm the algorithm
     * @param expected  the expected KCV hex string
     * @return true if the KCV matches
     */
    public static boolean verifyDekKcv(byte[] key, AlgorithmId algorithm, String expected) {
        return computeDekKcv(key, algorithm).equals(expected);
    }

    /**
     * Verifies an HMAC key KCV against an expected value.
     *
     * @param hmacKey  the HMAC key
     * @param expected the expected KCV hex string
     * @return true if the KCV matches
     */
    public static boolean verifyHmacKcv(byte[] hmacKey, String expected) {
        return computeHmacKcv(hmacKey).equals(expected);
    }

    /**
     * Verifies a binding hash against an expected value.
     *
     * @param hmacKey  the HMAC key
     * @param dek      the data encryption key
     * @param expected the expected binding hex string
     * @return true if the binding matches
     */
    public static boolean verifyBinding(byte[] hmacKey, byte[] dek, String expected) {
        return computeBinding(hmacKey, dek).equals(expected);
    }
}
