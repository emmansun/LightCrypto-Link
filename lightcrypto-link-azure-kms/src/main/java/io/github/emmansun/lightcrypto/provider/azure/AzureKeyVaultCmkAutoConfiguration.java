package io.github.emmansun.lightcrypto.provider.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.security.PublicKey;

/**
 * Auto-configuration for Azure Key Vault CMK provider.
 * <p>
 * Activated when {@code lcl.crypto.azure.vault-uri} is set. Creates an
 * {@link AzureKeyVaultCmkProvider} bean that takes precedence over the default
 * {@code LocalSymmetricCmkProvider} via {@code @ConditionalOnMissingBean}.
 * </p>
 * <p>
 * Startup flow: build credential -&gt; build CryptographyClient -&gt; call {@code getKey()}
 * to resolve key version and (optionally) public key.
 * </p>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(AzureKeyVaultCmkProvider.class)
@ConditionalOnProperty(prefix = "lcl.crypto.azure", name = "vault-uri")
@EnableConfigurationProperties(AzureKeyVaultCmkProperties.class)
public class AzureKeyVaultCmkAutoConfiguration {

    private static final String ALGORITHM_RSA = "RSA-OAEP-256";

    @Bean
    @ConditionalOnMissingBean(CmkProvider.class)
    public CmkProvider cmkProvider(AzureKeyVaultCmkProperties properties) {
        validateProperties(properties);

        TokenCredential credential = buildTokenCredential(properties);
        CryptographyClient cryptoClient = buildCryptographyClient(properties, credential);

        // Always call getKey() to resolve key version (and optionally public key)
        KeyVaultKey vaultKey = cryptoClient.getKey();
        String keyVersion = vaultKey.getProperties().getVersion();

        PublicKey publicKey = resolvePublicKey(properties, vaultKey);

        log.info("Azure Key Vault CMK provider initialized: vaultUri={}, keyName={}, keyVersion={}, algorithm={}",
                properties.getVaultUri(), properties.getKeyName(), keyVersion, properties.getAlgorithm());

        return new AzureKeyVaultCmkProvider(publicKey, cryptoClient, ALGORITHM_RSA, keyVersion);
    }

    private void validateProperties(AzureKeyVaultCmkProperties properties) {
        if (properties.getVaultUri() == null || properties.getVaultUri().isBlank()) {
            throw new IllegalArgumentException("lcl.crypto.azure.vault-uri must not be null or blank");
        }
        if (properties.getKeyName() == null || properties.getKeyName().isBlank()) {
            throw new IllegalArgumentException("lcl.crypto.azure.key-name must not be null or blank");
        }
        validateCredentials(properties);
    }

    private void validateCredentials(AzureKeyVaultCmkProperties properties) {
        boolean hasTenant = properties.getTenantId() != null && !properties.getTenantId().isBlank();
        boolean hasClient = properties.getClientId() != null && !properties.getClientId().isBlank();
        boolean hasSecret = properties.getClientSecret() != null && !properties.getClientSecret().isBlank();

        if (!hasTenant && !hasClient && !hasSecret) {
            // Will use DefaultAzureCredential — that's fine
            return;
        }
        if (hasTenant && hasClient && hasSecret) {
            // Will use ClientSecretCredential — good
            return;
        }
        throw new IllegalArgumentException(
                "Azure AD credentials must be fully configured: "
                        + "set all three of tenant-id, client-id, client-secret "
                        + "or none of them (to use DefaultAzureCredential)");
    }

    private TokenCredential buildTokenCredential(AzureKeyVaultCmkProperties properties) {
        boolean hasTenant = properties.getTenantId() != null && !properties.getTenantId().isBlank();
        if (hasTenant) {
            log.info("Using Azure AD service principal authentication");
            return new ClientSecretCredentialBuilder()
                    .tenantId(properties.getTenantId())
                    .clientId(properties.getClientId())
                    .clientSecret(properties.getClientSecret())
                    .build();
        }
        log.info("Using DefaultAzureCredential (managed identity / Azure CLI / environment)");
        return new DefaultAzureCredentialBuilder().build();
    }

    private CryptographyClient buildCryptographyClient(AzureKeyVaultCmkProperties properties,
                                                        TokenCredential credential) {
        try {
            String keyIdentifier = properties.getVaultUri().endsWith("/")
                    ? properties.getVaultUri() + "keys/" + properties.getKeyName()
                    : properties.getVaultUri() + "/keys/" + properties.getKeyName();
            return new CryptographyClientBuilder()
                    .keyIdentifier(keyIdentifier)
                    .credential(credential)
                    .buildClient();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to create Azure Key Vault CryptographyClient: " + e.getMessage(), e);
        }
    }

    private PublicKey resolvePublicKey(AzureKeyVaultCmkProperties properties, KeyVaultKey vaultKey) {
        String pem = properties.getPublicKey();
        if (pem != null && !pem.isBlank()) {
            log.info("Using pre-configured public key (algorithm={})", properties.getAlgorithm());
            return PublicKeyLoader.loadFromPem(pem, properties.getAlgorithm());
        }

        log.info("Extracting public key from Azure Key Vault response: keyName={}", properties.getKeyName());
        return JsonWebKeyToPublicKey.convert(vaultKey.getKey());
    }
}
