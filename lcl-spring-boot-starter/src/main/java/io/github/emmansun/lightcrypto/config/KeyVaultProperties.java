package io.github.emmansun.lightcrypto.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Key Vault configuration properties.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "lightcrypto.keyvault")
public class KeyVaultProperties {

    /** Cache configuration. */
    @Valid
    private Cache cache = new Cache();

    /**
     * Cache inner class — DEK/HMAC key cache settings.
     */
    @Data
    public static class Cache {

        /**
         * TTL for the in-memory DEK/HMAC key cache. After this duration, cached key material
         * is securely zeroed and evicted on the next access, forcing a reload from vault.
         * <p>
         * Default: 1 hour ({@code PT1H}). Set to {@code PT0S} to disable caching entirely.
         * </p>
         */
        private Duration ttl = Duration.ofHours(1);

        /** Maximum number of entries in the cache (default: 10000). */
        @Min(0)
        private int maxEntries = 10000;
    }
}
