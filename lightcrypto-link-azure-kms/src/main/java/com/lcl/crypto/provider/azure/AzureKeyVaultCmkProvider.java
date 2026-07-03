package com.lcl.crypto.provider.azure;

import com.lcl.crypto.model.WrappedKey;
import com.lcl.crypto.provider.CmkProvider;

/**
 * Azure Key Vault CMK provider — wraps/unwraps DEKs using RSA-OAEP via Azure Key Vault.
 * <p>
 * This is a v2 roadmap skeleton. Actual implementation requires:
 * <ul>
 *   <li>azure-security-keyvault-keys SDK</li>
 *   <li>CryptographyClient wrap/unwrap with RSA-OAEP-256</li>
 *   <li>Azure AD authentication (DefaultAzureCredential or service principal)</li>
 * </ul>
 * </p>
 *
 * <h3>Configuration</h3>
 * <pre>
 * lcl:
 *   crypto:
 *     azure:
 *       vault-uri: https://myvault.vault.azure.net
 *       key-name: my-cmk-key
 * </pre>
 */
public class AzureKeyVaultCmkProvider implements CmkProvider {

    private static final String PROVIDER_ID = "azure-keyvault";
    private static final String ALGORITHM = "RSA-OAEP-256";

    private final String vaultUri;
    private final String keyName;
    private final String keyVersion;

    public AzureKeyVaultCmkProvider(String vaultUri, String keyName, String keyVersion) {
        this.vaultUri = vaultUri;
        this.keyName = keyName;
        this.keyVersion = keyVersion;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public WrappedKey wrap(byte[] plaintextKey) {
        // TODO: Implement Azure Key Vault wrap using CryptographyClient
        // 1. Build CryptographyClient with keyIdentifier = vaultUri/keys/keyName[/keyVersion]
        // 2. Call wrapKey(KeyWrapAlgorithm.RSA_OAEP_256, plaintextKey)
        // 3. Return WrappedKey(result.getEncryptedKey(), "RSA-OAEP-256")
        throw new UnsupportedOperationException(
                "Azure Key Vault provider is a v2 roadmap feature. " +
                "Use LocalSymmetricCmkProvider (lcl.crypto.cmk) for production.");
    }

    @Override
    public byte[] unwrap(WrappedKey wrappedKey) {
        // TODO: Implement Azure Key Vault unwrap using CryptographyClient
        // 1. Build CryptographyClient with keyIdentifier
        // 2. Call unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrappedKey.ciphertext())
        // 3. Return result.getKey()
        throw new UnsupportedOperationException(
                "Azure Key Vault provider is a v2 roadmap feature. " +
                "Use LocalSymmetricCmkProvider (lcl.crypto.cmk) for production.");
    }
}
