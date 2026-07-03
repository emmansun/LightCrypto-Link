package io.emmansun.lightcrypto.provider.azure;

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

    /** Key version (optional, defaults to latest). */
    private String keyVersion;
}
