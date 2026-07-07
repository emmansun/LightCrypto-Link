package io.github.emmansun.lightcrypto.listener;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.exception.EncryptionException;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.model.PathSegmentType;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonBinaryWriter;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.github.emmansun.lightcrypto.service.TypeSerializer.resolveTypeMarker;

/**
 * Pre-save encryption listener — encrypts @Encrypted field values and replaces them
 * with BSON sub-documents on BeforeSaveEvent.
 */
@Slf4j
public class CryptoBeforeSaveListener {

    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec();

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
            applyEncryption(meta, source, document, dek, hmacKey, activeKid, 0);
        }
    }

    private void applyEncryption(EncryptedFieldMetadata meta,
                                 Object javaContext,
                                 Document bsonContext,
                                 byte[] dek,
                                 byte[] hmacKey,
                                 String activeKid,
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
                if (value == null) {
                    return;
                }

                if (isLeaf) {
                    if (meta.wholeObject()) {
                        Object rawBsonValue = bsonContext.get(segment);
                        if (rawBsonValue instanceof Document rawDoc) {
                            byte[] serialized = serializeStructuredValue(rawDoc, "DOC");
                            bsonContext.put(segment, buildEncryptedSubDoc(meta, serialized, "DOC", dek, activeKid));
                        }
                        return;
                    }
                    bsonContext.put(segment, buildEncryptedSubDoc(meta, value, dek, hmacKey, activeKid));
                    return;
                }

                Object nestedDoc = bsonContext.get(segment);
                if (nestedDoc instanceof Document nextDoc) {
                    applyEncryption(meta, value, nextDoc, dek, hmacKey, activeKid, depth + 1);
                }
                return;
            }

            if (pathType == PathSegmentType.LIST_ITER) {
                Object rawCollection = accessor.invoke(javaContext);
                if (!(rawCollection instanceof Collection<?> collection)) {
                    return;
                }

                Object rawBsonArray = bsonContext.get(segment);
                if (!(rawBsonArray instanceof List<?> bsonListRaw)) {
                    return;
                }

                List<Object> bsonList = new ArrayList<>(bsonListRaw.size());
                bsonList.addAll((List<Object>) bsonListRaw);
                if (bsonList != rawBsonArray) {
                    bsonContext.put(segment, bsonList);
                }

                List<?> javaList = (rawCollection instanceof List<?> list)
                        ? list
                        : new ArrayList<>(collection);

                if (isLeaf && meta.wholeObject()) {
                    byte[] serialized = serializeStructuredValue(bsonList, "COL");
                    bsonContext.put(segment, buildEncryptedSubDoc(meta, serialized, "COL", dek, activeKid));
                    return;
                }

                int size = Math.min(javaList.size(), bsonList.size());
                for (int i = 0; i < size; i++) {
                    Object item = javaList.get(i);
                    if (item == null) {
                        continue;
                    }

                    if (isLeaf) {
                        bsonList.set(i, buildEncryptedSubDoc(meta, item, dek, hmacKey, activeKid));
                    } else {
                        Object childDoc = bsonList.get(i);
                        if (childDoc instanceof Document childDocument) {
                            applyEncryption(meta, item, childDocument, dek, hmacKey, activeKid, depth + 1);
                        }
                    }
                }
                return;
            }

            if (pathType == PathSegmentType.MAP_ITER) {
                Object rawMap = accessor.invoke(javaContext);
                if (!(rawMap instanceof Map<?, ?> map)) {
                    return;
                }

                Object rawMapDoc = bsonContext.get(segment);
                if (!(rawMapDoc instanceof Document mapDoc)) {
                    return;
                }

                if (isLeaf && meta.wholeObject()) {
                    byte[] serialized = serializeStructuredValue(mapDoc, "MAP");
                    bsonContext.put(segment, buildEncryptedSubDoc(meta, serialized, "MAP", dek, activeKid));
                    return;
                }

                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    Object item = entry.getValue();
                    if (key == null || item == null) {
                        continue;
                    }
                    String keyName = String.valueOf(key);

                    if (isLeaf) {
                        mapDoc.put(keyName, buildEncryptedSubDoc(meta, item, dek, hmacKey, activeKid));
                    } else {
                        Object childDoc = mapDoc.get(keyName);
                        if (childDoc instanceof Document childDocument) {
                            applyEncryption(meta, item, childDocument, dek, hmacKey, activeKid, depth + 1);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            throw new EncryptionException("Failed to encrypt field path: " + meta.bsonFieldName(), e);
        }
    }

    private Document buildEncryptedSubDoc(EncryptedFieldMetadata meta,
                                          Object value,
                                          byte[] dek,
                                          byte[] hmacKey,
                                          String activeKid) {
        byte[] serialized = typeSerializer.serialize(value);
        SymmetricAlgorithm algorithm = meta.algorithm();
        byte[] encrypted = cryptoCodec.encrypt(dek, serialized, algorithm);

        Document subDoc = new Document();
        subDoc.put("c", new org.bson.types.Binary(encrypted));
        subDoc.put("_e", 1);
        subDoc.put("_t", resolveTypeMarker(meta.javaType()));
        subDoc.put("_k", activeKid);
        subDoc.put("_a", algorithm.name());

        if (meta.blindIndex()) {
            String blindIndex = cryptoCodec.generateBlindIndex(
                    hmacKey, meta.blindIndexFieldName(), serialized);
            subDoc.put("b", blindIndex);
        }
        return subDoc;
    }

    private Document buildEncryptedSubDoc(EncryptedFieldMetadata meta,
                                          byte[] serialized,
                                          String typeMarker,
                                          byte[] dek,
                                          String activeKid) {
        SymmetricAlgorithm algorithm = meta.algorithm();
        byte[] encrypted = cryptoCodec.encrypt(dek, serialized, algorithm);

        Document subDoc = new Document();
        subDoc.put("c", new org.bson.types.Binary(encrypted));
        subDoc.put("_e", 1);
        subDoc.put("_t", typeMarker);
        subDoc.put("_k", activeKid);
        subDoc.put("_a", algorithm.name());
        return subDoc;
    }

    @SuppressWarnings("unchecked")
    private byte[] serializeStructuredValue(Object value, String typeMarker) {
        Document payload = switch (typeMarker) {
            case "DOC", "MAP" -> (Document) value;
            case "COL" -> new Document("_v", value);
            default -> throw new IllegalArgumentException("Unsupported structured type marker: " + typeMarker);
        };

        try (BasicOutputBuffer buffer = new BasicOutputBuffer();
             BsonBinaryWriter writer = new BsonBinaryWriter(buffer)) {
            DOCUMENT_CODEC.encode(writer, payload, EncoderContext.builder().build());
            writer.flush();
            return buffer.toByteArray();
        }
    }
}
