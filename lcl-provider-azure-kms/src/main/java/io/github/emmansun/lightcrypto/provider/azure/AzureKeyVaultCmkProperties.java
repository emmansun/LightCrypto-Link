package io.github.emmansun.lightcrypto.provider.azure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Azure Key Vault CMK provider.
 */
@Data
@ConfigurationProperties(prefix = "lcl.crypto.azure")
public class AzureKeyVaultCmkProperties {

    /** Azure Key Vault URI (e.g. https://myvault.vault.azure.net). */
    private String vaultUri;

    /** Key name in the vault. */
    private String keyName;

    /** Azure AD tenant ID (optional — used with client-id/client-secret for service principal auth). */
    private String tenantId;

    /** Azure AD client ID (optional — used with tenant-id/client-secret for service principal auth). */
    private String clientId;

    /** Azure AD client secret (optional — used with tenant-id/client-id for service principal auth). */
    private String clientSecret;

    /**
     * Asymmetric algorithm for key wrapping: {@code RSA} (default).
     */
    private String algorithm = "RSA";

    /**
     * Optional PEM-encoded public key (X.509 SubjectPublicKeyInfo).
     * If not set, the provider fetches the public key from Key Vault via getKey() at startup.
     */
    private String publicKey;
}
