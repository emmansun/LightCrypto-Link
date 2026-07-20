package io.github.emmansun.lightcrypto.core.blindindex;

import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HKDF tenant-scoped blind index engine.
 *
 * <p>Derives a per-namespace HMAC key from a master key using HKDF-SHA256:
 * <pre>
 * derivedKey = HKDF-SHA256(IKM=masterHmacKey, Salt=SHA-256(namespace), Info="lcl-blind-index-v1", L=32)
 * </pre>
 *
 * <p>Blind index computation:
 * <pre>
 * index = Base64URL(HMAC-SHA-256(derivedKey, fieldName + ":" + normalizedValue))
 * </pre>
 *
 * <p>Value normalization:
 * <ul>
 *   <li>String: trim + lowercase</li>
 *   <li>byte[]: no normalization (used as-is)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class BlindIndexEngine {

    private static final String HKDF_INFO = "lcl-blind-index-v1";
    private static final int DERIVED_KEY_LENGTH = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte SEPARATOR = 0x3A; // ':'

    private final byte[] masterHmacKey;
    private final ConcurrentHashMap<String, byte[]> derivedKeyCache = new ConcurrentHashMap<>();

    /**
     * Creates a BlindIndexEngine with the given master HMAC key.
     *
     * @param masterHmacKey the master HMAC key (typically 32 bytes)
     */
    public BlindIndexEngine(byte[] masterHmacKey) {
        Objects.requireNonNull(masterHmacKey, "masterHmacKey must not be null");
        if (masterHmacKey.length == 0) {
            throw new IllegalArgumentException("masterHmacKey must not be empty");
        }
        this.masterHmacKey = masterHmacKey.clone();
    }

    /**
     * Computes a blind index for a string value.
     * The value is normalized (trim + lowercase) before hashing.
     *
     * @param namespace the namespace for key derivation
     * @param fieldName the field name (included in HMAC input)
     * @param value     the string value to index
     * @return Base64URL-encoded (no padding) HMAC-SHA-256 result
     */
    public String computeBlindIndex(Namespace namespace, String fieldName, String value) {
        Objects.requireNonNull(value, "value must not be null");
        String normalized = value.trim().toLowerCase();
        return computeBlindIndex(namespace, fieldName, normalized.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes a blind index for a byte[] value.
     * No normalization is applied.
     *
     * @param namespace the namespace for key derivation
     * @param fieldName the field name (included in HMAC input)
     * @param value     the raw bytes to index
     * @return Base64URL-encoded (no padding) HMAC-SHA-256 result
     */
    public String computeBlindIndex(Namespace namespace, String fieldName, byte[] value) {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        Objects.requireNonNull(value, "value must not be null");

        byte[] derivedKey = getDerivedKey(namespace);

        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(new SecretKeySpec(derivedKey, HMAC_ALGORITHM));
            hmac.update(fieldName.getBytes(StandardCharsets.UTF_8));
            hmac.update(SEPARATOR);
            hmac.update(value);
            byte[] result = hmac.doFinal();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA-256 computation failed", e);
        }
    }

    /**
     * Derives (or retrieves from cache) the per-namespace HMAC key.
     */
    private byte[] getDerivedKey(Namespace namespace) {
        String cacheKey = namespace.canonical();
        return derivedKeyCache.computeIfAbsent(cacheKey, k -> deriveKey(namespace));
    }

    /**
     * HKDF-SHA256 key derivation.
     * IKM = masterHmacKey
     * Salt = SHA-256(namespace canonical bytes)
     * Info = "lcl-blind-index-v1"
     * L = 32
     */
    private byte[] deriveKey(Namespace namespace) {
        try {
            byte[] salt = MessageDigest.getInstance("SHA-256")
                    .digest(namespace.canonicalBytes());
            byte[] info = HKDF_INFO.getBytes(StandardCharsets.UTF_8);

            HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
            hkdf.init(new HKDFParameters(masterHmacKey, salt, info));

            byte[] derivedKey = new byte[DERIVED_KEY_LENGTH];
            hkdf.generateBytes(derivedKey, 0, DERIVED_KEY_LENGTH);
            return derivedKey;
        } catch (Exception e) {
            throw new IllegalStateException("HKDF key derivation failed", e);
        }
    }
}
