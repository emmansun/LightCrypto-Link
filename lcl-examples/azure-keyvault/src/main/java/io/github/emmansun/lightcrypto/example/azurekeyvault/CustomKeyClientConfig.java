package io.github.emmansun.lightcrypto.example.azurekeyvault;

import com.azure.core.http.HttpClient;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.InetSocketAddress;

/**
 * Example: providing a fully custom Azure Key Vault {@link KeyClient}.
 * <p>
 * Activate with {@code --spring.profiles.active=custom-client}.
 * When this bean is present, LCL skips internal client construction
 * and uses this client directly for all Key Vault operations.
 * </p>
 * <p>
 * Use this pattern when you need control over:
 * <ul>
 *   <li>HTTP proxy settings</li>
 *   <li>Custom retry policy</li>
 *   <li>Custom credential chain (e.g., managed identity with specific client ID)</li>
 *   <li>Custom HTTP pipeline (logging, metrics)</li>
 * </ul>
 * </p>
 */
@Configuration
@Profile("custom-client")
public class CustomKeyClientConfig {

    @Bean
    public KeyClient keyClient() {
        // Example: Netty HTTP client with proxy
        HttpClient httpClient = new NettyAsyncHttpClientBuilder()
                .proxy(new ProxyOptions(
                        ProxyOptions.Type.HTTP,
                        new InetSocketAddress("proxy.example.com", 8080)))
                .build();

        return new KeyClientBuilder()
                .vaultUrl(System.getenv("AZURE_VAULT_URI"))
                .credential(new DefaultAzureCredentialBuilder()
                        // Optionally specify managed identity client ID
                        // .managedIdentityClientId("your-client-id")
                        .build())
                .httpClient(httpClient)
                .buildClient();
    }
}
