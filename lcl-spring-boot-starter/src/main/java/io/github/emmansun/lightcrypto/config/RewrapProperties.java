package io.github.emmansun.lightcrypto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for cross-CMK provider re-wrap migration.
 * <p>
 * Target provider resolution priority:
 * <ol>
 *   <li>{@code target-bean-name} — direct Spring bean name lookup (highest priority)</li>
 *   <li>{@code target-provider-id} + {@code target-public-reference} — dual match</li>
 *   <li>{@code target-provider-id} alone — single match (backward compatible)</li>
 * </ol>
 */
@Data
@ConfigurationProperties(prefix = "lightcrypto.migration.rewrap")
public class RewrapProperties {

    /**
     * Whether the re-wrap runner is enabled. Default: false.
     */
    private boolean enabled = false;

    /**
     * Whether to run in dry-run mode (validation only, no mutation). Default: true.
     */
    private boolean dryRun = true;

    /**
     * The provider ID of the target CMK provider to re-wrap keys under.
     * Must match a registered CmkProvider bean's getProviderId().
     */
    private String targetProviderId;

    /**
     * Optional: the public reference of the target CMK provider for disambiguation.
     * Used when multiple providers share the same providerId (e.g., same-type key rotation).
     * Must match the target CmkProvider bean's getPublicReference().
     */
    private String targetPublicReference;

    /**
     * Optional: the Spring bean name of the target CmkProvider.
     * Takes highest priority over providerId/publicReference matching.
     * Useful for same-type key migration where providerId is identical.
     */
    private String targetBeanName;
}
