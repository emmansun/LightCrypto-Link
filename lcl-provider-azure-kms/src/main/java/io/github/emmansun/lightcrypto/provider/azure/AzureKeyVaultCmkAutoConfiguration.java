package io.github.emmansun.lightcrypto.provider.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
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
import java.time.Duration;

/**
 * Auto-configuration for Azure Key Vault CMK provider.
 * <p>
 * Activated when {@code lcl.crypto.azure.key-name} is set. Creates an
 * {@link AzureKeyVaultCmkProvider} bean that takes precedence over the default
 * {@code LocalSymmetricCmkProvider} via {@code @ConditionalOnMissingBean}.
 * </p>
 * <p>
 * Two client modes:
 * <ul>
 *   <li>Default: builds {@link KeyClient} from {@code vault-uri} + credentials</li>
 *   <li>Custom: application provides its own {@code KeyClient} bean (skips internal construction)</li>
 * </ul>
 * </p>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(AzureKeyVaultCmkProvider.class)
@ConditionalOnProperty(prefix = "lcl.crypto.azure", name = "key-name")
@EnableConfigurationProperties(AzureKeyVaultCmkProperties.class)
public class AzureKeyVaultCmkAutoConfiguration {

    private static final String ALGORITHM_RSA = "RSA-OAEP-256";

    private static final int TOKEN_MAX_RETRY_COUNT = 3;

    private static final long TOKEN_MAX_RETRY_TIMEOUT = 10L;

    /**
     * Auto-configures the Azure Key Vault {@link KeyClient} from properties.
     * <p>
     * Skipped when the application provides its own {@code KeyClient} bean,
     * allowing full control over HTTP pipeline, proxy, retry policy, and credential chain.
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(KeyClient.class)
    public KeyClient azureKeyClient(AzureKeyVaultCmkProperties properties) {
        if (properties.getVaultUri() == null || properties.getVaultUri().isBlank()) {
            throw new IllegalArgumentException(
                    "lcl.crypto.azure.vault-uri must not be null or blank when no custom KeyClient bean is provided");
        }
        validateCredentials(properties);
        TokenCredential credential = buildTokenCredential(properties);
        return buildKeyClient(properties, credential);
    }

    @Bean
    @ConditionalOnMissingBean(CmkProvider.class)
    public CmkProvider cmkProvider(AzureKeyVaultCmkProperties properties, KeyClient keyClient) {
        if (properties.getKeyName() == null || properties.getKeyName().isBlank()) {
            throw new IllegalArgumentException("lcl.crypto.azure.key-name must not be null or blank");
        }

        KeyVaultKey key = keyClient.getKey(properties.getKeyName());
        String keyVersion = key.getProperties().getVersion();

        PublicKey publicKey = resolvePublicKey(properties, key);

        log.info("Azure Key Vault CMK provider initialized: keyName={}, keyVersion={}, algorithm={}",
                properties.getKeyName(), keyVersion, properties.getAlgorithm());

        return new AzureKeyVaultCmkProvider(publicKey, keyClient, ALGORITHM_RSA, properties.getKeyName(), keyVersion);
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
                    .maxRetry(TOKEN_MAX_RETRY_COUNT)
                    .retryTimeout(d -> Duration.ofSeconds(TOKEN_MAX_RETRY_TIMEOUT))
                    .build();
        }
        log.info("Using DefaultAzureCredential (managed identity / Azure CLI / environment)");
        return new DefaultAzureCredentialBuilder().build();
    }

    private KeyClient buildKeyClient(AzureKeyVaultCmkProperties properties,
                                                        TokenCredential credential) {
        try {
            return new KeyClientBuilder()
                    .vaultUrl(properties.getVaultUri())
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
