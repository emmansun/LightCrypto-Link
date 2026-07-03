package io.emmansun.lightcrypto.example.azurekeyvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo application for LightCrypto-Link with Azure Key Vault as CMK provider.
 * DEK wrapping is done locally using RSA-OAEP (zero network overhead),
 * unwrapping is done via Key Vault CryptographyClient.unwrapKey().
 */
@SpringBootApplication
public class AzureKeyVaultApplication {
    public static void main(String[] args) {
        SpringApplication.run(AzureKeyVaultApplication.class, args);
    }
}
