package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.service.FieldCryptoService;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.projection.EntityProjection;

/**
 * Custom MappingMongoConverter (Spring Boot 4.x variant) — decrypts @Encrypted fields
 * during read().
 *
 * <p>This class is intentionally duplicated in the v4 adapter module because the SB3
 * adapter's version was compiled against spring-data-commons 3.x where
 * {@code EntityProjection.getDomainType()} returned
 * {@code org.springframework.data.util.TypeInformation}. In spring-data-commons 4.x
 * the return type moved to {@code org.springframework.data.core.TypeInformation},
 * causing a {@code NoSuchMethodError} at runtime.
 *
 * @since 1.0.0
 */
@Slf4j
public class CryptoMappingMongoConverter extends MappingMongoConverter {

    private final EntityMetadataCache metadataCache;
    private final FieldCryptoService fieldCryptoService;

    public CryptoMappingMongoConverter(
            MongoDatabaseFactory mongoDbFactory,
            MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext,
            EntityMetadataCache metadataCache,
            FieldCryptoService fieldCryptoService) {
        super(new DefaultDbRefResolver(mongoDbFactory), mappingContext);
        this.metadataCache = metadataCache;
        this.fieldCryptoService = fieldCryptoService;
    }

    @Override
    public <S> S read(Class<S> clazz, Bson source) {
        if (source instanceof Document document && metadataCache.hasEncryptedFields(clazz)) {
            decryptFields(document, clazz);
        }
        return super.read(clazz, source);
    }

    @Override
    public <R> R project(EntityProjection<R, ?> projection, Bson source) {
        if (source instanceof Document document) {
            Class<?> domainType = projection.getDomainType().getType();
            if (metadataCache.hasEncryptedFields(domainType)) {
                decryptFields(document, domainType);
            }
        }
        return super.project(projection, source);
    }

    protected void decryptFields(Document document, Class<?> entityClass) {
        fieldCryptoService.decryptDocument(document, entityClass);
    }
}
