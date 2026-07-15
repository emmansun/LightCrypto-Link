package io.github.emmansun.lightcrypto.config;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LCL encryption configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "lcl.crypto")
public class CryptoProperties {

    /** Whether to enable the encryption feature. */
    private boolean enabled = true;

    /** CMK (Customer Master Key) — 64-character hex string = 32-byte symmetric key. */
    private String cmk;

    /** Global default symmetric algorithm for @Encrypted fields (default: AES_256_GCM). */
    private SymmetricAlgorithm algorithm = SymmetricAlgorithm.AES_256_GCM;

    /** Database name where the Key Vault collection resides (defaults to the application database). */
    private String keyVaultDatabase;

    /** Auto-initialize the vault on first startup (default true). */
    private boolean autoInit = true;

    /**
     * TTL for the in-memory DEK/HMAC key cache. After this duration, cached key material
     * is securely zeroed and evicted on the next access, forcing a reload from MongoDB.
     * <p>
     * Default: 1 hour ({@code PT1H}). Set to {@code PT0S} to disable caching entirely.
     * </p>
     */
    private Duration cacheTtl = Duration.ofHours(1);
}
