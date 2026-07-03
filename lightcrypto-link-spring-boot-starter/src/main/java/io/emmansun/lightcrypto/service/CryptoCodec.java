package io.emmansun.lightcrypto.service;

import io.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.emmansun.lightcrypto.exception.CryptoException;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.digests.SHA256Digest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Core crypto engine — multi-algorithm encryption/decryption + HMAC-SHA-256 blind index
 * + KCV/Binding computation.
 * <p>
 * Uses strategy pattern to dispatch to algorithm-specific encryptors:
 * AES-256-GCM, AES-256-CBC, SM4-GCM, SM4-CBC.
 */
public class CryptoCodec {

    private final Map<SymmetricAlgorithm, SymmetricEncryptor> encryptors;

    /**
     * Create a CryptoCodec with all supported algorithms.
     */
    public CryptoCodec() {
        this.encryptors = new EnumMap<>(SymmetricAlgorithm.class);
        registerEncryptor(new AesGcmEncryptor());
        registerEncryptor(new AesCbcEncryptor());
        registerEncryptor(new Sm4GcmEncryptor());
        registerEncryptor(new Sm4CbcEncryptor());
    }

    private void registerEncryptor(SymmetricEncryptor encryptor) {
        encryptors.put(encryptor.getAlgorithm(), encryptor);
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

        HMac hmac = new HMac(new SHA256Digest());
        hmac.init(new KeyParameter(hmacKey));
        hmac.update(input, 0, input.length);

        byte[] result = new byte[hmac.getMacSize()];
        hmac.doFinal(result, 0);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
    }

    /**
     * Compute a dual-key binding fingerprint: HMAC-SHA-256(hmacKey, dek).
     */
    public String computeBinding(byte[] hmacKey, byte[] dek) {
        HMac hmac = new HMac(new SHA256Digest());
        hmac.init(new KeyParameter(hmacKey));
        hmac.update(dek, 0, dek.length);

        byte[] result = new byte[hmac.getMacSize()];
        hmac.doFinal(result, 0);

        return java.util.HexFormat.of().formatHex(result);
    }

    private SymmetricEncryptor getEncryptor(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = encryptors.get(algorithm);
        if (encryptor == null) {
            throw new CryptoException("Unsupported algorithm: " + algorithm);
        }
        return encryptor;
    }
}
