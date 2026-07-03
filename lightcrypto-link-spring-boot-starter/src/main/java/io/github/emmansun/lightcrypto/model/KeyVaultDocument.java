package io.github.emmansun.lightcrypto.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Document mapping for the __lcl_keyvault collection.
 * <p>
 * Multi-DEK architecture: each entity class with @Encrypted fields has its own
 * vault document (_id = lcl-dek-{entitySimpleName}). Each vault maintains a
 * key version list supporting DEK rotation.
 * </p>
 */
@Data
@Document(collection = "__lcl_keyvault")
public class KeyVaultDocument {

    @Id
    private String id;

    private int v;

    private String status; // ACTIVE, ROTATED, DISABLED

    /** The kid of the currently active key entry. */
    private String activeKid;

    /** Key version entries (active + rotated + revoked). */
    private List<KeyVersionEntry> keys;

    private CmkInfo cmk;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * A single key version entry containing wrapped DEK/HMAC key pair,
     * integrity verification values, and lifecycle status.
     */
    @Data
    public static class KeyVersionEntry {
        /** Key version ID, e.g. "v1-a3b2c1d4". */
        private String kid;

        /** Key status: ACTIVE, ROTATED, REVOKED. */
        private String status;

        /** Wrapped DEK. */
        private WrappedKeyInfo dek;

        /** Wrapped HMAC key. */
        private WrappedKeyInfo hmk;

        /** Binding hash between DEK and HMAC key. */
        private String binding;

        private Instant createdAt;
    }

    @Data
    public static class WrappedKeyInfo {
        private byte[] wrapped;
        private String algorithm;
        private String kcv;
    }

    @Data
    public static class CmkInfo {
        private String provider;
        private String id;
    }
}
