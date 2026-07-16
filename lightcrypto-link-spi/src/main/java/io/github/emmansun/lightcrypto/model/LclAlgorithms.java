package io.github.emmansun.lightcrypto.model;

/**
 * Standard, cloud-neutral algorithm identifiers for LightCrypto-Link (LCL).
 * These strings are used in the persistence layer ('_a' field) and shared across 
 * Java and Node.js ecosystems to ensure absolute cross-language consistency.
 */
public final class LclAlgorithms {

    private LclAlgorithms() {
        // Prevent instantiation
    }

    // --- Symmetric Algorithms / Key Wrap Algorithms ---
    public static final String AES_256_GCM = "AES-256-GCM";
    public static final String SM4_GCM     = "SM4-GCM";
    public static final String SM4_CBC     = "SM4-CBC";

    // --- Asymmetric / Key Encryption Algorithms (Key Encryption Key - KEK) ---
    /**
     * Standard RSA OAEP with SHA-256 and MGF1 with SHA-256.
     * Compliant with RFC 7518 (JWA) and natively understood by Azure Key Vault.
     */
    public static final String RSA_OAEP_256 = "RSA-OAEP-256";

    // --- Special Cloud-Specific Symmetric Wrapper ---
    /**
     * Representative marker for cloud-managed symmetric Key-Encryption-Key.
     * Maps to KMS_DATA_KEY in Alibaba Cloud KMS.
     */
    public static final String KMS_DATA_KEY = "KMS-DATA-KEY";
}

