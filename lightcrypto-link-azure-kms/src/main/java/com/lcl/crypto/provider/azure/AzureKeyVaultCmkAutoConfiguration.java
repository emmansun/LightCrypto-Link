package com.lcl.crypto.provider.azure;

import com.lcl.crypto.provider.CmkProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Azure Key Vault CMK provider.
 * Activated when {@code lcl.crypto.azure.vault-uri} is set.
 */
@AutoConfiguration
@ConditionalOnClass(AzureKeyVaultCmkProvider.class)
@ConditionalOnProperty(prefix = "lcl.crypto.azure", name = "vault-uri")
@EnableConfigurationProperties(AzureKeyVaultCmkProperties.class)
public class AzureKeyVaultCmkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CmkProvider.class)
    public CmkProvider cmkProvider(AzureKeyVaultCmkProperties properties) {
        return new AzureKeyVaultCmkProvider(
                properties.getVaultUri(),
                properties.getKeyName(),
                properties.getKeyVersion());
    }
}
