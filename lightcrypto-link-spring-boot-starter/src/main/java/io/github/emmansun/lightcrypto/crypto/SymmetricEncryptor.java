package io.github.emmansun.lightcrypto.crypto;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;

/**
 * Strategy interface for symmetric encryption algorithms.
 * Each implementation handles encrypt/decrypt/KCV for a specific algorithm.
 */
public interface SymmetricEncryptor {

    /**
     * Encrypt plaintext and return IV || ciphertext.
     *
     * @param key       the encryption key (32 bytes for AES, 16 bytes for SM4)
     * @param plaintext the data to encrypt
     * @return IV concatenated with ciphertext
     */
    byte[] encrypt(byte[] key, byte[] plaintext);

    /**
     * Decrypt IV || ciphertext and return the plaintext.
     *
     * @param key  the encryption key (32 bytes for AES, 16 bytes for SM4)
     * @param data IV concatenated with ciphertext
     * @return the decrypted plaintext
     */
    byte[] decrypt(byte[] key, byte[] data);

    /**
     * Compute the Key Check Value (KCV) for the given key.
     * Uses a deterministic all-zero IV for reproducibility.
     *
     * @param key the encryption key
     * @return lowercase hex string of the KCV
     */
    String computeKcv(byte[] key);

    /**
     * Get the algorithm this encryptor handles.
     *
     * @return the SymmetricAlgorithm enum value
     */
    SymmetricAlgorithm getAlgorithm();
}
