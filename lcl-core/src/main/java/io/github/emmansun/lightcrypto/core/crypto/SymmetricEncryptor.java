package io.github.emmansun.lightcrypto.core.crypto;

import io.github.emmansun.lightcrypto.core.format.AlgorithmId;

/**
 * Strategy interface for symmetric encryption algorithms in lcl-core.
 *
 * <p>Unlike the legacy starter interface, this version:
 * <ul>
 *   <li>Accepts IV as an explicit parameter (generated externally)</li>
 *   <li>Accepts AAD bytes for authenticated encryption modes (GCM)</li>
 *   <li>Returns raw ciphertext only (IV is stored separately in Wire Format)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public interface SymmetricEncryptor {

    /**
     * Encrypts plaintext with the given key, IV, and optional AAD.
     *
     * @param key       the encryption key (32 bytes for AES-256, 16 bytes for SM4)
     * @param iv        the initialization vector (12 bytes for GCM, 16 bytes for CBC)
     * @param plaintext the data to encrypt
     * @param aad       additional authenticated data (used by GCM modes; ignored by CBC modes)
     * @return the ciphertext (GCM: CT‖Tag(16B); CBC: PKCS7-padded CT)
     */
    byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext, byte[] aad);

    /**
     * Decrypts ciphertext with the given key, IV, and optional AAD.
     *
     * @param key        the encryption key
     * @param iv         the initialization vector
     * @param ciphertext the ciphertext (GCM: CT‖Tag; CBC: PKCS7-padded CT)
     * @param aad        additional authenticated data (must match encryption AAD for GCM)
     * @return the decrypted plaintext
     */
    byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext, byte[] aad);

    /**
     * Computes the Key Check Value (KCV) for the given key.
     * Uses a deterministic all-zero IV for reproducibility.
     *
     * @param key the encryption key
     * @return lowercase hex string of the KCV
     */
    String computeKcv(byte[] key);

    /**
     * Returns the algorithm identifier for this encryptor.
     *
     * @return the AlgorithmId enum value
     */
    AlgorithmId algorithmId();
}
