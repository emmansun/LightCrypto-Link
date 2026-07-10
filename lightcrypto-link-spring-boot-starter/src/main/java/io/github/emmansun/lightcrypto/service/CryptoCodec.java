package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
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
    static {
        if (java.security.Security.getProvider("BC") == null) {
            try {
                Class<?> clazz = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
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
        loadOptionalEncryptor("io.github.emmansun.lightcrypto.service.Sm4GcmEncryptor");
        loadOptionalEncryptor("io.github.emmansun.lightcrypto.service.Sm4CbcEncryptor");
    }

    private void registerEncryptor(SymmetricEncryptor encryptor) {
        encryptors.put(encryptor.getAlgorithm(), encryptor);
    }

    private void loadOptionalEncryptor(String className) {
        if (java.security.Security.getProvider("BC") == null) {
            log.warn("Bouncy Castle provider not found, skipping optional encryptor: {}", className);
            return;
        }
        try {
            Class<?> clazz = Class.forName(className);
            
            SymmetricEncryptor instance = (SymmetricEncryptor) clazz.getDeclaredConstructor().newInstance();
            
            registerEncryptor(instance);
        } catch (Exception e) {
            log.warn("Failed to load optional encryptor: {}", className, e);
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
        byte[] fieldNameBytes = fieldName.getBytes(StandardCharsets.UTF_8);
        // Build HMAC input: fieldNameBytes + ":" + serializedValue
        byte[] input = new byte[fieldNameBytes.length + 1 + serializedValue.length];
        System.arraycopy(fieldNameBytes, 0, input, 0, fieldNameBytes.length);
        input[fieldNameBytes.length] = 0x3A; // colon separator
        System.arraycopy(serializedValue, 0, input, fieldNameBytes.length + 1, serializedValue.length);

        byte[] result = calculateHmacSHA256(hmacKey, input);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
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
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            return hmac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to initialize HmacSHA256", e);
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
