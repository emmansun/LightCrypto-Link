package io.github.emmansun.lightcrypto.config;

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

import java.util.HexFormat;

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
@ConditionalOnProperty(prefix = "lcl.crypto", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CryptoProperties.class)
public class LightCryptoLinkAutoConfiguration {

    // ===== Core services =====

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "lcl.crypto", name = "cmk")
    public CmkProvider cmkProvider(CryptoProperties properties) {
        byte[] cmk = HexFormat.of().parseHex(properties.getCmk());
        return new LocalSymmetricCmkProvider(cmk);
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
    public EntityMetadataCache entityMetadataCache(CryptoProperties properties) {
        return new EntityMetadataCache(properties);
    }
}
