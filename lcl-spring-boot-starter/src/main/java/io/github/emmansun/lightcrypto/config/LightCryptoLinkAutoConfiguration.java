package io.github.emmansun.lightcrypto.config;

import io.github.emmansun.lightcrypto.exception.ConfigurationException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.provider.LocalSymmetricCmkProvider;
import io.github.emmansun.lightcrypto.service.TypeDeserializer;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.validation.Validator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

/**
 * Storage-agnostic auto-configuration for Light Crypto Link.
 *
 * <p>Registers core encryption services that do NOT depend on any specific database.
 * Database-specific beans (listeners, converters, repository factories, KeyVaultService)
 * are registered by adapter modules (e.g., lcl-adapter-mongodb).
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "lightcrypto", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({
        RuntimeProperties.class,
        CryptographyProperties.class,
        KeyVaultProperties.class,
        KmsProperties.class,
        TenantProperties.class,
        ObservabilityProperties.class
})
public class LightCryptoLinkAutoConfiguration {

    // ===== Core services =====

    @Bean
    @ConditionalOnMissingBean
    @Conditional(LocalSymmetricProviderCondition.class)
    public CmkProvider cmkProvider(KmsProperties kmsProperties) {
        List<KmsProperties.ProviderEntry> providers = kmsProperties.getProviders();
        for (KmsProperties.ProviderEntry entry : providers) {
            if (entry.getType() == KmsProperties.ProviderType.LOCAL_SYMMETRIC) {
                byte[] cmk = resolveKeyHex(entry);
                return new LocalSymmetricCmkProvider(cmk);
            }
        }
        // Unreachable: the condition guarantees a LOCAL_SYMMETRIC entry exists
        throw new ConfigurationException("No LOCAL_SYMMETRIC provider found.");
    }

    @Bean
    public TypeSerializer typeSerializer() {
        return new TypeSerializer();
    }

    @Bean
    public TypeDeserializer typeDeserializer() {
        return new TypeDeserializer();
    }

    @Bean
    public EntityMetadataCache entityMetadataCache(CryptographyProperties cryptographyProperties,
                                                    TenantProperties tenantProperties) {
        return new EntityMetadataCache(cryptographyProperties, tenantProperties);
    }

    @Bean
    public Validator lightCryptoConfigurationValidator() {
        return new ConfigurationValidator();
    }

    // ===== Key resolution =====

    /**
     * Resolve the hex key from a provider entry, supporting both inline keyHex and keyHexFile.
     * If both are set, keyHex takes precedence.
     */
    static byte[] resolveKeyHex(KmsProperties.ProviderEntry entry) {
        String keyHex = entry.getKeyHex();
        if (keyHex != null && !keyHex.isBlank()) {
            return HexFormat.of().parseHex(keyHex.strip());
        }
        String keyHexFile = entry.getKeyHexFile();
        if (keyHexFile != null && !keyHexFile.isBlank()) {
            Path path = Path.of(keyHexFile.strip());
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8).strip();
                return HexFormat.of().parseHex(content);
            } catch (IOException e) {
                throw new ConfigurationException(
                        "Failed to read keyHexFile '" + keyHexFile + "' for provider '" + entry.getId() + "': " + e.getMessage(), e);
            }
        }
        throw new ConfigurationException(
                "LOCAL_SYMMETRIC provider '" + entry.getId() + "' must have either 'keyHex' or 'keyHexFile' configured.");
    }

    /**
     * Condition that matches when {@code lightcrypto.kms.providers} contains at least one
     * entry with {@code type=LOCAL_SYMMETRIC}. When this condition is false, the starter
     * skips CmkProvider creation, allowing cloud KMS provider modules to register their own.
     */
    static class LocalSymmetricProviderCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String type = context.getEnvironment().getProperty("lightcrypto.kms.providers[0].type");
            if ("LOCAL_SYMMETRIC".equals(type)) {
                return true;
            }
            // Check additional indices (up to 5) in case LOCAL_SYMMETRIC is not the first entry
            for (int i = 1; i <= 5; i++) {
                String entryType = context.getEnvironment().getProperty("lightcrypto.kms.providers[" + i + "].type");
                if (entryType == null) break;
                if ("LOCAL_SYMMETRIC".equals(entryType)) return true;
            }
            return false;
        }
    }
}
