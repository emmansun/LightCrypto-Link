package io.github.emmansun.lightcrypto.listener;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.projection.EntityProjection;

import java.util.List;

/**
 * Custom MappingMongoConverter — decrypts @Encrypted fields during read().
 * <p>
 * Instead of the BeforeConvertEvent approach, this directly modifies the BSON Document
 * before the Document → Entity conversion, replacing encrypted sub-documents with
 * their decrypted original values.
 */
@Slf4j
public class CryptoMappingMongoConverter extends MappingMongoConverter {

    private final EntityMetadataCache metadataCache;
    private final CryptoCodec cryptoCodec;
    private final TypeDeserializer typeDeserializer;
    private final KeyVaultService keyVaultService;

    public CryptoMappingMongoConverter(
            MongoDatabaseFactory mongoDbFactory,
            MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext,
            EntityMetadataCache metadataCache,
            CryptoCodec cryptoCodec,
            TypeDeserializer typeDeserializer,
            KeyVaultService keyVaultService) {
        super(new DefaultDbRefResolver(mongoDbFactory), mappingContext);
        this.metadataCache = metadataCache;
        this.cryptoCodec = cryptoCodec;
        this.typeDeserializer = typeDeserializer;
        this.keyVaultService = keyVaultService;
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
        List<EncryptedFieldMetadata> fields = metadataCache.getEncryptedFields(entityClass);
        for (EncryptedFieldMetadata meta : fields) {
            Object raw = document.get(meta.fieldName());
            if (!(raw instanceof Document subDoc)) continue;

            Integer eMarker = subDoc.getInteger("_e");
            if (eMarker == null || eMarker != 1) continue;

            String typeMarker = subDoc.getString("_t");
            Binary cipherBinary = subDoc.get("c", Binary.class);
            if (cipherBinary == null) continue;

            // Read kid from sub-document
            String kid = subDoc.getString("_k");
            if (kid == null) {
                throw new FatalCryptoException(
                        "Encrypted sub-document for field '" + meta.fieldName() +
                                "' is missing '_k' (kid) field. Incompatible with multi-DEK format.");
            }

            // Read algorithm from sub-document, default to AES_256_GCM for backward compatibility
            String algorithmName = subDoc.getString("_a");
            SymmetricAlgorithm algorithm = algorithmName != null
                    ? SymmetricAlgorithm.valueOf(algorithmName)
                    : SymmetricAlgorithm.AES_256_GCM;

            // Decrypt using kid-specific DEK and algorithm
            byte[] dek = keyVaultService.getDek(kid);
            byte[] plaintext = cryptoCodec.decrypt(dek, cipherBinary.getData(), algorithm);

            // Deserialize
            Object value = typeDeserializer.deserialize(typeMarker, plaintext);

            // Replace encrypted sub-document with original value
            document.put(meta.fieldName(), value);

            log.debug("Decrypted field '{}' for entity {} using kid {} and algorithm {}",
                    meta.fieldName(), entityClass.getSimpleName(), kid, algorithm);
        }
    }
}
