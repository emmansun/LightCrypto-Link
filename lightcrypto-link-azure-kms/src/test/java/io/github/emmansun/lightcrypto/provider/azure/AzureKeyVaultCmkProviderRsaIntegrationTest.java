package io.github.emmansun.lightcrypto.provider.azure;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Azure Key Vault CMK Provider using real Azure Key Vault.
 * Skipped when Azure credentials are not configured.
 */
@EnabledIfEnvironmentVariable(named = "AZURE_VAULT_URI", matches = ".+")
class AzureKeyVaultCmkProviderRsaIntegrationTest {

    private static AzureKeyVaultCmkProvider provider;
    private static String keyVersion;

    @BeforeAll
    static void setupProvider() throws Exception {
        String vaultUri = System.getenv("AZURE_VAULT_URI");
        String keyName = System.getenv("AZURE_KEY_NAME");
        String tenantId = System.getenv("AZURE_TENANT_ID");
        String clientId = System.getenv("AZURE_CLIENT_ID");
        String clientSecret = System.getenv("AZURE_CLIENT_SECRET");

        var credentialBuilder = new ClientSecretCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientSecret(clientSecret);

        String keyIdentifier = vaultUri.endsWith("/")
                ? vaultUri + "keys/" + keyName
                : vaultUri + "/keys/" + keyName;

        CryptographyClient cryptoClient = new CryptographyClientBuilder()
                .keyIdentifier(keyIdentifier)
                .credential(credentialBuilder.build())
                .buildClient();

        // Get key version and public key from Azure Key Vault
        KeyVaultKey vaultKey = cryptoClient.getKey();
        keyVersion = vaultKey.getProperties().getVersion();
        PublicKey publicKey = JsonWebKeyToPublicKey.convert(vaultKey.getKey());

        provider = new AzureKeyVaultCmkProvider(publicKey, cryptoClient, "RSA-OAEP-256", keyVersion);
    }

    @Test
    void rsaRoundTrip_shouldEncryptAndDecryptSuccessfully() {
        byte[] originalKey = new byte[32];
        new SecureRandom().nextBytes(originalKey);

        WrappedKey wrapped = provider.wrap(originalKey);
        byte[] unwrapped = provider.unwrap(wrapped);

        assertThat(unwrapped).isEqualTo(originalKey);
    }

    @Test
    void rsaWrap_shouldProduceDifferentCiphertextsForSameInput() {
        byte[] originalKey = new byte[32];
        new SecureRandom().nextBytes(originalKey);

        WrappedKey wrapped1 = provider.wrap(originalKey);
        WrappedKey wrapped2 = provider.wrap(originalKey);

        assertThat(wrapped1.ciphertext()).isNotEqualTo(wrapped2.ciphertext());
    }

    @Test
    void getProviderId_shouldReturnAzureKeyVault() {
        assertThat(provider.getProviderId()).isEqualTo("azure-keyvault");
    }

    @Test
    void getKeyVersion_shouldReturnAutoResolvedVersion() {
        assertThat(keyVersion).isNotNull().isNotEmpty();
        assertThat(provider.getKeyVersion()).isEqualTo(keyVersion);
    }

    @Test
    void rsaRoundTrip_multipleKeys_shouldAllSucceed() {
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 5; i++) {
            byte[] key = new byte[32];
            random.nextBytes(key);
            WrappedKey wrapped = provider.wrap(key);
            byte[] unwrapped = provider.unwrap(wrapped);
            assertThat(unwrapped).isEqualTo(key);
        }
    }
}
