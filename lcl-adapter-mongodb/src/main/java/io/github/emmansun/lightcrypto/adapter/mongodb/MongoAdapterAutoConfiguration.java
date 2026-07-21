package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.config.KeyVaultProperties;
import io.github.emmansun.lightcrypto.config.LightCryptoLinkAutoConfiguration;
import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.FieldCryptoService;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.ProgrammaticCryptoService;
import io.github.emmansun.lightcrypto.service.TypeDeserializer;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.spi.*;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
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

import java.util.List;

/**
 * Spring Boot auto-configuration for MongoDB storage adapter.
 *
 * <p>Activates when {@link MongoTemplate} is on the classpath and after
 * {@link LightCryptoLinkAutoConfiguration} has registered core beans.
 *
 * <p>Registers all MongoDB-specific infrastructure:
 * <ul>
 *   <li>{@link MongoVaultStore} — VaultStore implementation</li>
 *   <li>{@link MongoStorageAdapter} — StorageAdapter implementation</li>
 *   <li>{@link MongoQueryTransformer} — QueryTransformer implementation</li>
 *   <li>{@link BsonDocumentAccessor} — DocumentAccessor implementation</li>
 *   <li>{@link BsonStructuredValueCodec} — StructuredValueCodec implementation</li>
 *   <li>{@link MongoEncryptHandler} — EncryptHandler implementation</li>
 *   <li>{@link MongoDecryptHandler} — DecryptHandler implementation</li>
 *   <li>{@link CryptoBeforeSaveListener} — BeforeSaveEvent encryption listener</li>
 *   <li>{@link CryptoMappingMongoConverter} — Custom converter for decryption</li>
 *   <li>{@link CryptoMongoQueryCreator} — Query rewriting for blind index</li>
 * </ul>
 *
 * @since 1.0.0
 */
@AutoConfiguration(after = {LightCryptoLinkAutoConfiguration.class, MongoAutoConfiguration.class})
@ConditionalOnClass(MongoTemplate.class)
@ConditionalOnProperty(prefix = "lightcrypto.adapters.mongodb", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MongoAdapterProperties.class)
@EnableMongoRepositories(
        basePackages = "io.github.emmansun.lightcrypto",
        repositoryFactoryBeanClass = CryptoMongoRepositoryFactoryBean.class
)
public class MongoAdapterAutoConfiguration {

    // ===== Infrastructure beans =====

    @Bean
    @ConditionalOnMissingBean(VaultStore.class)
    public MongoVaultStore mongoVaultStore(MongoTemplate mongoTemplate, MongoAdapterProperties adapterProperties) {
        return new MongoVaultStore(mongoTemplate, adapterProperties.getKeyVaultCollection());
    }

    @Bean
    @ConditionalOnMissingBean(StorageAdapter.class)
    public MongoStorageAdapter mongoStorageAdapter() {
        return new MongoStorageAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(QueryTransformer.class)
    public MongoQueryTransformer mongoQueryTransformer(HmacKeyProvider hmacKeyProvider,
                                                       BlindIndexFieldChecker fieldChecker) {
        return new MongoQueryTransformer(hmacKeyProvider, fieldChecker);
    }

    // ===== SPI implementations =====

    @Bean
    @ConditionalOnMissingBean(DocumentAccessor.class)
    public BsonDocumentAccessor bsonDocumentAccessor() {
        return new BsonDocumentAccessor();
    }

    @Bean
    @ConditionalOnMissingBean(StructuredValueCodec.class)
    public BsonStructuredValueCodec bsonStructuredValueCodec() {
        return new BsonStructuredValueCodec();
    }

    // ===== Core crypto services (require SPI implementations) =====

    @Bean
    @ConditionalOnMissingBean(KeyVaultService.class)
    public KeyVaultService keyVaultService(VaultStore vaultStore, CmkProvider cmkProvider,
                                           KeyVaultProperties keyVaultProperties) {
        return new KeyVaultService(vaultStore, cmkProvider, keyVaultProperties);
    }

    @Bean
    @ConditionalOnMissingBean(BlindIndexEngine.class)
    public BlindIndexEngine blindIndexEngine(KeyVaultService keyVaultService) {
        return new BlindIndexEngine(new byte[32]);
    }

    @Bean
    @ConditionalOnMissingBean(HmacKeyProvider.class)
    public HmacKeyProvider hmacKeyProvider(@Lazy KeyVaultService keyVaultService) {
        return keyVaultService::getActiveHmacKey;
    }

    @Bean
    @ConditionalOnMissingBean(BlindIndexFieldChecker.class)
    public BlindIndexFieldChecker blindIndexFieldChecker(EntityMetadataCache metadataCache) {
        return (field, entityType) -> {
            List<EncryptedFieldMetadata> fields = metadataCache.getEncryptedFields(entityType);
            return fields.stream().anyMatch(f -> f.bsonFieldName().equals(field) && f.blindIndex());
        };
    }

    @Bean
    @ConditionalOnMissingBean(FieldCryptoService.class)
    public FieldCryptoService fieldCryptoService(EntityMetadataCache metadataCache,
                                                 TypeDeserializer typeDeserializer,
                                                 @Lazy KeyVaultService keyVaultService,
                                                 StorageAdapter storageAdapter,
                                                 DocumentAccessor documentAccessor,
                                                 StructuredValueCodec structuredValueCodec) {
        return new FieldCryptoService(metadataCache, typeDeserializer, keyVaultService,
                storageAdapter, documentAccessor, structuredValueCodec);
    }

    @Bean
    @ConditionalOnMissingBean(ProgrammaticCryptoService.class)
    public ProgrammaticCryptoService programmaticCryptoService(TypeSerializer typeSerializer,
                                                               TypeDeserializer typeDeserializer,
                                                               @Lazy KeyVaultService keyVaultService,
                                                               FieldCryptoService fieldCryptoService,
                                                               StructuredValueCodec structuredValueCodec) {
        return new ProgrammaticCryptoService(
                typeSerializer,
                typeDeserializer,
                keyVaultService,
                fieldCryptoService,
                structuredValueCodec);
    }

    // ===== Encryption/Decryption handlers =====

    @Bean
    public MongoEncryptHandler mongoEncryptHandler(EntityMetadataCache metadataCache,
                                                   TypeSerializer typeSerializer,
                                                   @Lazy KeyVaultService keyVaultService,
                                                   StorageAdapter storageAdapter,
                                                   StructuredValueCodec structuredValueCodec) {
        return new MongoEncryptHandler(metadataCache, typeSerializer, keyVaultService,
                storageAdapter, structuredValueCodec);
    }

    @Bean
    public MongoDecryptHandler mongoDecryptHandler(FieldCryptoService fieldCryptoService) {
        return new MongoDecryptHandler(fieldCryptoService);
    }

    // ===== Event listener =====

    @Bean
    public CryptoBeforeSaveListener cryptoBeforeSaveListener(EntityMetadataCache metadataCache,
                                                             TypeSerializer typeSerializer,
                                                             KeyVaultService keyVaultService,
                                                             BlindIndexEngine blindIndexEngine,
                                                             StorageAdapter storageAdapter,
                                                             StructuredValueCodec structuredValueCodec) {
        return new CryptoBeforeSaveListener(metadataCache, typeSerializer, keyVaultService,
                blindIndexEngine, storageAdapter, structuredValueCodec);
    }

    // ===== Query rewriting =====

    @Bean
    @ConditionalOnMissingBean(CryptoMongoQueryCreator.class)
    public CryptoMongoQueryCreator cryptoMongoQueryCreator(EntityMetadataCache metadataCache,
                                                           TypeSerializer typeSerializer,
                                                           KeyVaultService keyVaultService,
                                                           QueryTransformer queryTransformer) {
        return new CryptoMongoQueryCreator(metadataCache, typeSerializer, keyVaultService, queryTransformer);
    }

    // ===== Custom converter =====

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

    // ===== Lifecycle hooks =====

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
