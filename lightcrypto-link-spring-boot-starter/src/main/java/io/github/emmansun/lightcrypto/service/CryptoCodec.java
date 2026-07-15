package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.crypto.AesCbcEncryptor;
import io.github.emmansun.lightcrypto.crypto.AesGcmEncryptor;
import io.github.emmansun.lightcrypto.crypto.Sm4CbcEncryptor;
import io.github.emmansun.lightcrypto.crypto.Sm4GcmEncryptor;
import io.github.emmansun.lightcrypto.crypto.SymmetricEncryptor;
import io.github.emmansun.lightcrypto.exception.CryptoException;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Core crypto engine — multi-algorithm encryption/decryption + HMAC-SHA-256 blind index
 * + KCV/Binding computation.
 * <p>
 * Uses strategy pattern to dispatch to algorithm-specific encryptors:
 * AES-256-GCM, AES-256-CBC, SM4-GCM, SM4-CBC.
 */
@Slf4j
public class CryptoCodec {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String BC_PROVIDER_NAME = "BC";
    private static final String BC_PROVIDER_CLASS = "org.bouncycastle.jce.provider.BouncyCastleProvider";

    static {
        if (java.security.Security.getProvider(BC_PROVIDER_NAME) == null) {
            try {
                Class<?> clazz = Class.forName(BC_PROVIDER_CLASS);
                java.security.Provider provider = (java.security.Provider) clazz.getDeclaredConstructor().newInstance();
                java.security.Security.addProvider(provider);
            } catch (ReflectiveOperationException e) {
                // ignore
            }
        }
    }

    private final Map<SymmetricAlgorithm, SymmetricEncryptor> encryptors;

    /**
     * Create a CryptoCodec with all supported algorithms.
     */
    public CryptoCodec() {
        this.encryptors = new EnumMap<>(SymmetricAlgorithm.class);
        registerEncryptor(new AesGcmEncryptor());
        registerEncryptor(new AesCbcEncryptor());
        loadOptionalEncryptor(Sm4GcmEncryptor.class);
        loadOptionalEncryptor(Sm4CbcEncryptor.class);
    }

    private void registerEncryptor(SymmetricEncryptor encryptor) {
        encryptors.put(encryptor.getAlgorithm(), encryptor);
    }

    private void loadOptionalEncryptor(Class<? extends SymmetricEncryptor> clazz) {
        try {
            SymmetricEncryptor encryptor = clazz.getDeclaredConstructor().newInstance();
            registerEncryptor(encryptor);
        } catch (Throwable e) {
            log.warn("Skip optional encryptor {}: {}", clazz.getSimpleName(), e.getMessage());
        }
    }

    /**
     * Encrypt plaintext using the specified algorithm.
     *
     * @param dek        the Data Encryption Key (32 bytes)
     * @param plaintext  the data to encrypt
     * @param algorithm  the symmetric algorithm to use
     * @return IV concatenated with ciphertext
     */
    public byte[] encrypt(byte[] dek, byte[] plaintext, SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = getEncryptor(algorithm);
        return encryptor.encrypt(dek, plaintext);
    }

    /**
     * Decrypt data using the specified algorithm.
     *
     * @param dek       the Data Encryption Key (32 bytes)
     * @param data      IV concatenated with ciphertext
     * @param algorithm the symmetric algorithm to use
     * @return the decrypted plaintext
     */
    public byte[] decrypt(byte[] dek, byte[] data, SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = getEncryptor(algorithm);
        return encryptor.decrypt(dek, data);
    }

    /**
     * Encrypt plaintext using the default algorithm (AES-256-GCM).
     * Deprecated: use {@link #encrypt(byte[], byte[], SymmetricAlgorithm)} instead.
     */
    @Deprecated
    public byte[] encrypt(byte[] dek, byte[] plaintext) {
        return encrypt(dek, plaintext, SymmetricAlgorithm.AES_256_GCM);
    }

    /**
     * Decrypt data using the default algorithm (AES-256-GCM).
     * Deprecated: use {@link #decrypt(byte[], byte[], SymmetricAlgorithm)} instead.
     */
    @Deprecated
    public byte[] decrypt(byte[] dek, byte[] data) {
        return decrypt(dek, data, SymmetricAlgorithm.AES_256_GCM);
    }

    /**
     * Compute KCV using the specified algorithm.
     *
     * @param key       the encryption key
     * @param algorithm the symmetric algorithm to use
     * @return lowercase hex string of the KCV
     */
    public String computeKcv(byte[] key, SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = getEncryptor(algorithm);
        return encryptor.computeKcv(key);
    }

    /**
     * Compute KCV using the default algorithm (AES-256-GCM).
     * Deprecated: use {@link #computeKcv(byte[], SymmetricAlgorithm)} instead.
     */
    @Deprecated
    public String computeKcv(byte[] key) {
        return computeKcv(key, SymmetricAlgorithm.AES_256_GCM);
    }

    /**
     * Generate a blind index: HMAC-SHA-256(hmacKey, fieldName + ":" + serializedValue).
     * Accepts byte[] for the serialized value and outputs base64url without padding.
     */
    public String generateBlindIndex(byte[] hmacKey, String fieldName, byte[] serializedValue) {
        try {
            Mac hmac = Mac.getInstance(HMAC_SHA256);
            hmac.init(new SecretKeySpec(hmacKey, HMAC_SHA256));
            hmac.update(fieldName.getBytes(StandardCharsets.UTF_8));
            // Build HMAC input: fieldNameBytes + ":" + serializedValue
            hmac.update((byte) 0x3A); // colon separator
            hmac.update(serializedValue);
            byte[] result = hmac.doFinal();

            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (Exception e) {
            throw new CryptoException("Failed to generate blind index", e);
        }
    }

    /**
     * Compute a dual-key binding fingerprint: HMAC-SHA-256(hmacKey, dek).
     */
    public String computeBinding(byte[] hmacKey, byte[] dek) {
        byte[] result = calculateHmacSHA256(hmacKey, dek);
        return java.util.HexFormat.of().formatHex(result);
    }

    private static byte[] calculateHmacSHA256(byte[] key, byte[] data) {
        try {
            Mac hmac = Mac.getInstance(HMAC_SHA256);
            hmac.init(new SecretKeySpec(key, HMAC_SHA256));
            return hmac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new CryptoException("Failed to initialize HmacSHA256", e);
        }
    }

    private SymmetricEncryptor getEncryptor(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = encryptors.get(algorithm);
        if (encryptor == null) {
            throw new CryptoException("Unsupported algorithm: " + algorithm);
        }
        return encryptor;
    }
}
