package io.github.emmansun.lightcrypto.provider.azure;

import com.azure.core.credential.TokenCredential;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureKeyVaultCmkAutoConfigurationTest {

    private final AzureKeyVaultCmkAutoConfiguration autoConfiguration = new AzureKeyVaultCmkAutoConfiguration();

    @Test
    void azureKeyClient_shouldRejectBlankVaultUri() {
        AzureKeyVaultCmkProperties properties = validProperties();
        properties.setVaultUri(" ");

        assertThatThrownBy(() -> autoConfiguration.azureKeyClient(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vault-uri must not be null or blank");
    }

    @Test
    void cmkProvider_shouldRejectBlankKeyName() {
        AzureKeyVaultCmkProperties properties = validProperties();
        properties.setKeyName(" ");

        assertThatThrownBy(() -> autoConfiguration.cmkProvider(properties, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lcl.crypto.azure.key-name must not be null or blank");
    }

    @Test
    void validateCredentials_shouldRejectPartialCredentials() throws Exception {
        AzureKeyVaultCmkProperties properties = validProperties();
        properties.setTenantId("tenant");
        properties.setClientId("client");
        properties.setClientSecret(null);

        assertThatThrownBy(() -> invokePrivate("validateCredentials", new Class[]{AzureKeyVaultCmkProperties.class}, properties))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .satisfies(ex -> assertThat(ex.getCause().getMessage()).contains("must be fully configured"));
    }

    @Test
    void validateCredentials_shouldAcceptAllOrNone() throws Exception {
        AzureKeyVaultCmkProperties none = validProperties();
        none.setTenantId(null);
        none.setClientId(null);
        none.setClientSecret(null);
        invokePrivate("validateCredentials", new Class[]{AzureKeyVaultCmkProperties.class}, none);

        AzureKeyVaultCmkProperties all = validProperties();
        all.setTenantId("tenant");
        all.setClientId("client");
        all.setClientSecret("secret");
        invokePrivate("validateCredentials", new Class[]{AzureKeyVaultCmkProperties.class}, all);
    }

    @Test
    void buildTokenCredential_shouldCreateCredentialForDefaultAndClientSecret() throws Exception {
        AzureKeyVaultCmkProperties defaultCred = validProperties();
        defaultCred.setTenantId(null);
        defaultCred.setClientId(null);
        defaultCred.setClientSecret(null);
        Object c1 = invokePrivate("buildTokenCredential", new Class[]{AzureKeyVaultCmkProperties.class}, defaultCred);

        AzureKeyVaultCmkProperties servicePrincipal = validProperties();
        servicePrincipal.setTenantId("tenant");
        servicePrincipal.setClientId("client");
        servicePrincipal.setClientSecret("secret");
        Object c2 = invokePrivate("buildTokenCredential", new Class[]{AzureKeyVaultCmkProperties.class}, servicePrincipal);

        assertThat(c1).isInstanceOf(TokenCredential.class);
        assertThat(c2).isInstanceOf(TokenCredential.class);
    }

    @Test
    void resolvePublicKey_shouldUseConfiguredPemWhenPresent() throws Exception {
        AzureKeyVaultCmkProperties properties = validProperties();
        properties.setPublicKey(generateRsaPem());

        Object key = invokePrivate(
                "resolvePublicKey",
                new Class[]{AzureKeyVaultCmkProperties.class, com.azure.security.keyvault.keys.models.KeyVaultKey.class},
                properties,
                null
        );

        assertThat(key).isInstanceOf(java.security.PublicKey.class);
        assertThat(((java.security.PublicKey) key).getAlgorithm()).isEqualTo("RSA");
    }

    private Object invokePrivate(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = AzureKeyVaultCmkAutoConfiguration.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(autoConfiguration, args);
    }

    private AzureKeyVaultCmkProperties validProperties() {
        AzureKeyVaultCmkProperties properties = new AzureKeyVaultCmkProperties();
        properties.setVaultUri("https://dummy.vault.azure.net");
        properties.setKeyName("dummy-key");
        properties.setAlgorithm("RSA");
        return properties;
    }

    private String generateRsaPem() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        String base64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        StringBuilder builder = new StringBuilder();
        builder.append("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            builder.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        builder.append("-----END PUBLIC KEY-----");
        return builder.toString();
    }
}
