package io.github.emmansun.lightcrypto.config;

import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.listener.CryptoBeforeSaveListener;
import io.github.emmansun.lightcrypto.listener.CryptoMappingMongoConverter;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.provider.LocalSymmetricCmkProvider;
import io.github.emmansun.lightcrypto.query.CryptoMongoQueryCreator;
import io.github.emmansun.lightcrypto.query.CryptoMongoRepositoryFactoryBean;
import io.github.emmansun.lightcrypto.service.*;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.HexFormat;

@AutoConfiguration
@ConditionalOnProperty(prefix = "lcl.crypto", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CryptoProperties.class)
@EnableMongoRepositories(
        basePackages = "io.github.emmansun.lightcrypto",
        repositoryFactoryBeanClass = CryptoMongoRepositoryFactoryBean.class
)
public class LightCryptoLinkAutoConfiguration {
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

    @Bean
    public KeyVaultService keyVaultService(MongoTemplate mongoTemplate, CmkProvider cmkProvider,
                                           CryptoProperties properties) {
        return new KeyVaultService(mongoTemplate, cmkProvider, properties);
    }

    @Bean
    public CryptoBeforeSaveListener cryptoBeforeSaveListener(EntityMetadataCache metadataCache,
                                                             TypeSerializer typeSerializer,
                                                             KeyVaultService keyVaultService,
                                                             BlindIndexEngine blindIndexEngine) {
        return new CryptoBeforeSaveListener(metadataCache, typeSerializer, keyVaultService, blindIndexEngine);
    }

    @Bean
    public BlindIndexEngine blindIndexEngine(KeyVaultService keyVaultService) {
        // BlindIndexEngine needs a master HMAC key. We use a lazy approach:
        // The engine is created with a placeholder; actual per-namespace engines
        // are created on-demand in the listener/query creator using the vault's HMAC key.
        // This bean serves as a marker/placeholder for DI.
        return new BlindIndexEngine(new byte[32]);
    }

    @Bean
    public FieldCryptoService fieldCryptoService(EntityMetadataCache metadataCache,
                                                 TypeDeserializer typeDeserializer,
                                                 @Lazy KeyVaultService keyVaultService) {
        return new FieldCryptoService(metadataCache, typeDeserializer, keyVaultService);
    }

    @Bean
    public ProgrammaticCryptoService programmaticCryptoService(TypeSerializer typeSerializer,
                                                               TypeDeserializer typeDeserializer,
                                                               @Lazy KeyVaultService keyVaultService,
                                                               FieldCryptoService fieldCryptoService) {
        return new ProgrammaticCryptoService(
                typeSerializer,
                typeDeserializer,
                keyVaultService,
                fieldCryptoService);
    }

    /**
     * Custom MappingMongoConverter — decrypts @Encrypted fields during read().
     */
    @Bean
    public MappingMongoConverter mappingMongoConverter(
            MongoDatabaseFactory mongoDbFactory,
            MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext,
            EntityMetadataCache metadataCache,
            FieldCryptoService fieldCryptoService) {
        return new CryptoMappingMongoConverter(
                mongoDbFactory, mappingContext, metadataCache, fieldCryptoService);
    }

    @Bean
    public CryptoMongoQueryCreator cryptoMongoQueryCreator(EntityMetadataCache metadataCache,
                                                           TypeSerializer typeSerializer,
                                                           KeyVaultService keyVaultService) {
        return new CryptoMongoQueryCreator(metadataCache, typeSerializer, keyVaultService);
    }

    /**
     * Pre-warms the EntityMetadataCache at startup.
     */
    @Bean
    public SmartInitializingSingleton entityMetadataCachePreWarmer(
            EntityMetadataCache metadataCache,
            MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext) {
        return () -> {
            for (MongoPersistentEntity<?> entity : mappingContext.getPersistentEntities()) {
                metadataCache.preWarm(entity.getType());
            }
        };
    }
}
