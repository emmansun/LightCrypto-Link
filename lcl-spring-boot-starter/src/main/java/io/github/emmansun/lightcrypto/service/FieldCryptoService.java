package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.WireFormatDecoder;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.exception.KeyManagementException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.model.PathSegmentType;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;

import java.util.List;

/**
 * Manual decryption service — allows users to decrypt @Encrypted fields
 * in a raw BSON Document obtained outside the transparent converter path
 * (e.g. aggregation pipelines, native driver queries, data migration scripts).
 * <p>
 * Encapsulates Wire Format V1 blob parsing, DEK lookup by namespace + dekVersion,
 * and type deserialization.
 */
@Slf4j
public class FieldCryptoService {

    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec();

    private final EntityMetadataCache metadataCache;
    private final TypeDeserializer typeDeserializer;
    private final KeyVaultService keyVaultService;

    public FieldCryptoService(EntityMetadataCache metadataCache,
                              TypeDeserializer typeDeserializer,
                              KeyVaultService keyVaultService) {
        this.metadataCache = metadataCache;
        this.typeDeserializer = typeDeserializer;
        this.keyVaultService = keyVaultService;
    }

    /**
     * Decrypt all @Encrypted fields in the given raw Document for the specified entity class.
     * Modifies the Document in-place and returns the same reference.
     */
    public Document decryptDocument(Document rawDocument, Class<?> entityClass) {
        if (rawDocument == null) {
            throw new IllegalArgumentException("rawDocument must not be null");
        }
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }

        List<EncryptedFieldMetadata> fields = metadataCache.getEncryptedFields(entityClass);
        if (fields.isEmpty()) {
            return rawDocument;
        }

        for (EncryptedFieldMetadata meta : fields) {
            decryptField(rawDocument, meta, 0);
        }
        return rawDocument;
    }

    @SuppressWarnings("unchecked")
    private void decryptField(Document document, EncryptedFieldMetadata meta, int depth) {
        PathSegmentType pathType = meta.pathTypes().get(depth);
        String segment = meta.path().get(depth);
        boolean isLeaf = depth == meta.path().size() - 1;

        if (pathType == PathSegmentType.FIELD) {
            Object raw = document.get(segment);
            if (raw == null) {
                return;
            }
            if (isLeaf) {
                Object value = decryptSubDocument(raw, meta);
                if (value != null) {
                    document.put(segment, value);
                }
                return;
            }
            if (raw instanceof Document nestedDoc) {
                decryptField(nestedDoc, meta, depth + 1);
            }
            return;
        }

        if (pathType == PathSegmentType.LIST_ITER) {
            if (isLeaf && meta.wholeObject()) {
                Object raw = document.get(segment);
                Object value = decryptSubDocument(raw, meta);
                if (value != null) {
                    document.put(segment, value);
                }
                return;
            }

            Object rawArray = document.get(segment);
            if (!(rawArray instanceof List<?> array)) {
                return;
            }

            for (int i = 0; i < array.size(); i++) {
                Object raw = array.get(i);
                if (isLeaf) {
                    Object value = decryptSubDocument(raw, meta);
                    if (value != null) {
                        ((List<Object>) array).set(i, value);
                    }
                } else if (raw instanceof Document nestedDoc) {
                    decryptField(nestedDoc, meta, depth + 1);
                }
            }
            return;
        }

        if (pathType == PathSegmentType.MAP_ITER) {
            if (isLeaf && meta.wholeObject()) {
                Object raw = document.get(segment);
                Object value = decryptSubDocument(raw, meta);
                if (value != null) {
                    document.put(segment, value);
                }
                return;
            }

            Object rawMap = document.get(segment);
            if (!(rawMap instanceof Document mapDoc)) {
                return;
            }
            for (java.util.Map.Entry<String, Object> entry : mapDoc.entrySet()) {
                Object raw = entry.getValue();
                if (isLeaf) {
                    Object value = decryptSubDocument(raw, meta);
                    if (value != null) {
                        entry.setValue(value);
                    }
                } else if (raw instanceof Document nestedDoc) {
                    decryptField(nestedDoc, meta, depth + 1);
                }
            }
        }
    }

    private Object decryptSubDocument(Object raw, EncryptedFieldMetadata meta) {
        if (!(raw instanceof Document subDoc)) {
            return null;
        }

        Integer eMarker = subDoc.getInteger("_e");
        if (eMarker == null || eMarker != 1) {
            return null;
        }

        String typeMarker = subDoc.getString("_t");

        // Wire Format V1: "c" is a Base64URL string
        String blob = subDoc.getString("c");
        if (blob == null) {
            return null;
        }

        // Decode wire format to extract dekVersion for DEK lookup
        String namespace = meta.namespace().canonical();
        int dekVersion;
        try {
            WireFormatDecoder.DecodedBlob decoded = WireFormatDecoder.decodeFromBase64Url(blob);
            dekVersion = decoded.dekVersion();
        } catch (IllegalArgumentException ex) {
            throw new DecryptionException(
                "Invalid Wire Format blob for field '" + meta.bsonFieldName() + "'", ex);
        }

        // Resolve DEK by namespace + version
        byte[] dek;
        try {
            keyVaultService.ensureVaultInitialized(namespace);
            dek = keyVaultService.getDekByVersion(namespace, dekVersion);
        } catch (FatalCryptoException ex) {
            throw new KeyManagementException(
                "Failed to resolve DEK for field '" + meta.bsonFieldName()
                    + "' (namespace=" + namespace + ", dekVersion=" + dekVersion + ")", ex);
        }

        // Decrypt using lcl-core CryptoCodec
        byte[] plaintext;
        try {
            plaintext = CryptoCodec.decrypt(dek, blob);
        } catch (RuntimeException ex) {
            throw new DecryptionException(
                "Failed to decrypt field '" + meta.bsonFieldName()
                    + "' (namespace=" + namespace + ", dekVersion=" + dekVersion + ")", ex);
        }

        if ("DOC".equals(typeMarker) || "COL".equals(typeMarker) || "MAP".equals(typeMarker)) {
            return decodeStructuredValue(typeMarker, plaintext);
        }

        // Deserialize
        Object value;
        try {
            value = typeDeserializer.deserialize(typeMarker, plaintext);
        } catch (RuntimeException ex) {
            throw new DecryptionException(
                "Failed to deserialize field '" + meta.bsonFieldName() + "' with type marker '" + typeMarker + "'", ex);
        }

        log.debug("Decrypted field '{}' using namespace {} and dekVersion {}",
            meta.bsonFieldName(), namespace, dekVersion);
        return value;
    }

    private Object decodeStructuredValue(String typeMarker, byte[] plaintext) {
        Document payload;
        try {
            payload = new RawBsonDocument(plaintext).decode(DOCUMENT_CODEC);
        } catch (RuntimeException ex) {
            throw new DecryptionException("Failed to decode structured payload for type marker: " + typeMarker, ex);
        }

        return switch (typeMarker) {
            case "DOC", "MAP" -> payload;
            case "COL" -> payload.getList("_v", Object.class);
            default -> throw new DecryptionException("Unsupported structured type marker: " + typeMarker);
        };
    }
}
