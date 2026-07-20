package io.github.emmansun.lightcrypto.annotation;

/**
 * Symmetric encryption algorithm enum.
 * Supports AES-256 (GCM/CBC) and SM4 (GCM/CBC) for DEK encryption.
 */
public enum SymmetricAlgorithm {
    /** Sentinel value: use the global default from {@code lcl.crypto.algorithm}. */
    DEFAULT,

    /** AES-256-GCM: 12-byte IV, authenticated encryption (global default) */
    AES_256_GCM,

    /** AES-256-CBC: 16-byte IV, PKCS5 padding (legacy compatibility) */
    AES_256_CBC,

    /** SM4-GCM: 16-byte key, 12-byte IV, authenticated encryption (China compliance) */
    SM4_GCM,

    /** SM4-CBC: 16-byte key, 16-byte IV, PKCS5 padding (China compliance) */
    SM4_CBC
}
