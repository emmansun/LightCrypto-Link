package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.exception.EncryptionException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.model.PathSegmentType;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.spi.EncryptHandler;
import io.github.emmansun.lightcrypto.spi.StorageAdapter;
import io.github.emmansun.lightcrypto.spi.StructuredValueCodec;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.github.emmansun.lightcrypto.service.TypeSerializer.resolveTypeMarker;

/**
 * MongoDB-specific encryption handler that delegates to {@link KeyVaultService}
 * for key management and {@link StorageAdapter} for payload construction.
 *
 * <p>This handler is designed to work with BSON Document payloads.
 *
 * @since 1.0.0
 */
@Slf4j
public class MongoEncryptHandler implements EncryptHandler {

    private final EntityMetadataCache metadataCache;
    private final TypeSerializer typeSerializer;
    private final KeyVaultService keyVaultService;
    private final StorageAdapter storageAdapter;
    private final StructuredValueCodec structuredValueCodec;

    public MongoEncryptHandler(EntityMetadataCache metadataCache,
                               TypeSerializer typeSerializer,
                               KeyVaultService keyVaultService,
                               StorageAdapter storageAdapter,
                               StructuredValueCodec structuredValueCodec) {
        this.metadataCache = metadataCache;
        this.typeSerializer = typeSerializer;
        this.keyVaultService = keyVaultService;
        this.storageAdapter = storageAdapter;
        this.structuredValueCodec = structuredValueCodec;
    }

    @Override
    public void encrypt(Object document, Object entity, Class<?> entityClass) {
        if (!(document instanceof Document bsonDoc)) {
            return;
        }
        if (!metadataCache.hasEncryptedFields(entityClass)) {
            return;
        }

        List<EncryptedFieldMetadata> fields = metadataCache.getEncryptedFields(entityClass);
        for (EncryptedFieldMetadata meta : fields) {
            String namespace = meta.namespace().canonical();
            keyVaultService.ensureVaultInitialized(namespace);
            applyEncryption(meta, entity, bsonDoc, 0);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyEncryption(EncryptedFieldMetadata meta,
                                 Object javaContext,
                                 Document bsonContext,
                                 int depth) {
        if (javaContext == null || bsonContext == null) {
            return;
        }

        PathSegmentType pathType = meta.pathTypes().get(depth);
        String segment = meta.path().get(depth);
        MethodHandle accessor = meta.accessors().get(depth);
        boolean isLeaf = depth == meta.path().size() - 1;

        try {
            if (pathType == PathSegmentType.FIELD) {
                Object value = accessor.invoke(javaContext);
                if (value == null) return;

                if (isLeaf) {
                    if (meta.wholeObject()) {
                        Object rawBsonValue = bsonContext.get(segment);
                        if (rawBsonValue instanceof Document rawDoc) {
                            byte[] serialized = structuredValueCodec.encode(rawDoc, "DOC");
                            bsonContext.put(segment, buildEncryptedPayload(meta, serialized, "DOC"));
                        }
                        return;
                    }
                    bsonContext.put(segment, buildEncryptedPayload(meta, value));
                    return;
                }

                Object nestedDoc = bsonContext.get(segment);
                if (nestedDoc instanceof Document nextDoc) {
                    applyEncryption(meta, value, nextDoc, depth + 1);
                }
                return;
            }

            if (pathType == PathSegmentType.LIST_ITER) {
                Object rawCollection = accessor.invoke(javaContext);
                if (!(rawCollection instanceof Collection<?> collection)) return;

                Object rawBsonArray = bsonContext.get(segment);
                if (!(rawBsonArray instanceof List<?> bsonListRaw)) return;

                List<Object> bsonList = new ArrayList<>(bsonListRaw.size());
                bsonList.addAll((List<Object>) bsonListRaw);
                if (bsonList != rawBsonArray) {
                    bsonContext.put(segment, bsonList);
                }

                List<?> javaList = (rawCollection instanceof List<?> list)
                        ? list : new ArrayList<>(collection);

                if (isLeaf && meta.wholeObject()) {
                    byte[] serialized = structuredValueCodec.encode(bsonList, "COL");
                    bsonContext.put(segment, buildEncryptedPayload(meta, serialized, "COL"));
                    return;
                }

                int size = Math.min(javaList.size(), bsonList.size());
                for (int i = 0; i < size; i++) {
                    Object item = javaList.get(i);
                    if (item == null) continue;

                    if (isLeaf) {
                        bsonList.set(i, buildEncryptedPayload(meta, item));
                    } else {
                        Object childDoc = bsonList.get(i);
                        if (childDoc instanceof Document childDocument) {
                            applyEncryption(meta, item, childDocument, depth + 1);
                        }
                    }
                }
                return;
            }

            if (pathType == PathSegmentType.MAP_ITER) {
                Object rawMap = accessor.invoke(javaContext);
                if (!(rawMap instanceof Map<?, ?> map)) return;

                Object rawMapDoc = bsonContext.get(segment);
                if (!(rawMapDoc instanceof Document mapDoc)) return;

                if (isLeaf && meta.wholeObject()) {
                    byte[] serialized = structuredValueCodec.encode(mapDoc, "MAP");
                    bsonContext.put(segment, buildEncryptedPayload(meta, serialized, "MAP"));
                    return;
                }

                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    Object item = entry.getValue();
                    if (key == null || item == null) continue;
                    String keyName = String.valueOf(key);

                    if (isLeaf) {
                        mapDoc.put(keyName, buildEncryptedPayload(meta, item));
                    } else {
                        Object childDoc = mapDoc.get(keyName);
                        if (childDoc instanceof Document childDocument) {
                            applyEncryption(meta, item, childDocument, depth + 1);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            throw new EncryptionException("Failed to encrypt field path: " + meta.bsonFieldName(), e);
        }
    }

    private Object buildEncryptedPayload(EncryptedFieldMetadata meta, Object value) {
        byte[] serialized = typeSerializer.serialize(value);
        return buildEncryptedPayload(meta, serialized, resolveTypeMarker(meta.javaType()));
    }

    private Object buildEncryptedPayload(EncryptedFieldMetadata meta,
                                          byte[] serialized, String typeMarker) {
        String namespace = meta.namespace().canonical();
        int dekVersion = keyVaultService.getActiveDekVersion(namespace);
        byte[] dek = keyVaultService.getDek(keyVaultService.getActiveKid(namespace));

        String blob = CryptoCodec.encrypt(dek, serialized, meta.algorithmId(), meta.namespace(), dekVersion);

        String blindIndex = null;
        if (meta.blindIndex()) {
            byte[] hmacKey = keyVaultService.getActiveHmacKey(namespace);
            BlindIndexEngine engine = new BlindIndexEngine(hmacKey);
            blindIndex = engine.computeBlindIndex(meta.namespace(), meta.blindIndexFieldName(), serialized);
        }

        return storageAdapter.buildEncryptedPayload(blob, typeMarker, blindIndex);
    }
}
