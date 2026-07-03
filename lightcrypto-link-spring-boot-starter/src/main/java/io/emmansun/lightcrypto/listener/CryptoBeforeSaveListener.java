package io.emmansun.lightcrypto.listener;

import io.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.emmansun.lightcrypto.service.CryptoCodec;
import io.emmansun.lightcrypto.service.KeyVaultService;
import io.emmansun.lightcrypto.service.TypeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;

import java.util.List;

import static io.emmansun.lightcrypto.service.TypeSerializer.resolveTypeMarker;

/**
 * Pre-save encryption listener — encrypts @Encrypted field values and replaces them
 * with BSON sub-documents on BeforeSaveEvent.
 */
@Slf4j
public class CryptoBeforeSaveListener {

    private final EntityMetadataCache metadataCache;
    private final CryptoCodec cryptoCodec;
    private final TypeSerializer typeSerializer;
    private final KeyVaultService keyVaultService;

    public CryptoBeforeSaveListener(EntityMetadataCache metadataCache,
                                    CryptoCodec cryptoCodec,
                                    TypeSerializer typeSerializer,
                                    KeyVaultService keyVaultService) {
        this.metadataCache = metadataCache;
        this.cryptoCodec = cryptoCodec;
        this.typeSerializer = typeSerializer;
        this.keyVaultService = keyVaultService;
    }

    @EventListener
    public void onBeforeSave(BeforeSaveEvent<?> event) {
        Object source = event.getSource();
        if (source == null) return;

        Class<?> entityClass = source.getClass();
        if (!metadataCache.hasEncryptedFields(entityClass)) return;

        Document document = event.getDocument();
        if (document == null) return;

        List<EncryptedFieldMetadata> fields = metadataCache.getEncryptedFields(entityClass);

        // Ensure vault is initialized for this entity class
        keyVaultService.ensureVaultInitialized(entityClass);
        String activeKid = keyVaultService.getActiveKid(entityClass);
        byte[] dek = keyVaultService.getDek(activeKid);
        byte[] hmacKey = keyVaultService.getHmacKey(activeKid);

        for (EncryptedFieldMetadata meta : fields) {
            try {
                Object value = meta.field().get(source);
                if (value == null) continue; // Skip null fields

                // Serialize to byte[]
                byte[] serialized = typeSerializer.serialize(value);

                // Get algorithm from metadata
                SymmetricAlgorithm algorithm = meta.algorithm();

                // Encrypt with algorithm
                byte[] encrypted = cryptoCodec.encrypt(dek, serialized, algorithm);

                // Build BSON sub-document
                Document subDoc = new Document();
                subDoc.put("c", new org.bson.types.Binary(encrypted));
                subDoc.put("_e", 1);
                subDoc.put("_t", resolveTypeMarker(meta.javaType()));
                subDoc.put("_k", activeKid);
                subDoc.put("_a", algorithm.name());

                // Blind index
                if (meta.blindIndex()) {
                    String blindIndex = cryptoCodec.generateBlindIndex(
                            hmacKey, meta.effectiveFieldName(), serialized);
                    subDoc.put("b", blindIndex);
                }

                document.put(meta.fieldName(), subDoc);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access field: " + meta.fieldName(), e);
            }
        }
    }
}
