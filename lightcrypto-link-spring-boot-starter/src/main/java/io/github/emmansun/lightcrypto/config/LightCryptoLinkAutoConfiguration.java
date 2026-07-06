package io.github.emmansun.lightcrypto.config;

import io.github.emmansun.lightcrypto.listener.CryptoBeforeSaveListener;
import io.github.emmansun.lightcrypto.listener.CryptoMappingMongoConverter;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.provider.LocalSymmetricCmkProvider;
import io.github.emmansun.lightcrypto.query.CryptoMongoQueryCreator;
import io.github.emmansun.lightcrypto.query.CryptoMongoRepositoryFactoryBean;
import io.github.emmansun.lightcrypto.service.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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

import java.security.Security;
import java.util.HexFormat;

@AutoConfiguration
@ConditionalOnProperty(prefix = "lcl.crypto", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CryptoProperties.class)
@EnableMongoRepositories(
        basePackages = "io.github.emmansun.lightcrypto",
        repositoryFactoryBeanClass = CryptoMongoRepositoryFactoryBean.class
)
public class LightCryptoLinkAutoConfiguration {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "lcl.crypto", name = "cmk")
    public CmkProvider cmkProvider(CryptoProperties properties) {
        byte[] cmk = HexFormat.of().parseHex(properties.getCmk());
        return new LocalSymmetricCmkProvider(cmk);
    }

    @Bean
    public CryptoCodec cryptoCodec() {
        return new CryptoCodec();
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
                                           CryptoProperties properties, CryptoCodec cryptoCodec) {
        return new KeyVaultService(mongoTemplate, cmkProvider, properties, cryptoCodec);
    }

    @Bean
    public CryptoBeforeSaveListener cryptoBeforeSaveListener(EntityMetadataCache metadataCache,
                                                             CryptoCodec cryptoCodec,
                                                             TypeSerializer typeSerializer,
                                                             KeyVaultService keyVaultService) {
        return new CryptoBeforeSaveListener(metadataCache, cryptoCodec, typeSerializer, keyVaultService);
    }

    @Bean
    public FieldCryptoService fieldCryptoService(EntityMetadataCache metadataCache,
                                                 CryptoCodec cryptoCodec,
                                                 TypeDeserializer typeDeserializer,
                                                 @Lazy KeyVaultService keyVaultService) {
        return new FieldCryptoService(metadataCache, cryptoCodec, typeDeserializer, keyVaultService);
    }

    /**
     * Custom MappingMongoConverter — decrypts @Encrypted fields during read().
     * The @ConditionalOnMissingBean(MongoConverter.class) in Spring Boot's
     * MongoDataAutoConfiguration will skip creating the default converter.
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
                                                           CryptoCodec cryptoCodec,
                                                           TypeSerializer typeSerializer,
                                                           KeyVaultService keyVaultService) {
        return new CryptoMongoQueryCreator(metadataCache, cryptoCodec, typeSerializer, keyVaultService);
    }

    /**
     * Pre-warms the EntityMetadataCache at startup by scanning all registered
     * MongoDB entity classes, eliminating cold-start latency on first request.
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
