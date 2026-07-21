package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.WireFormatDecoder;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.exception.KeyManagementException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.model.PathSegmentType;
import io.github.emmansun.lightcrypto.spi.DocumentAccessor;
import io.github.emmansun.lightcrypto.spi.StorageAdapter;
import io.github.emmansun.lightcrypto.spi.StructuredValueCodec;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Manual decryption service — allows users to decrypt @Encrypted fields
 * in raw documents obtained outside the transparent converter path
 * (e.g. aggregation pipelines, native driver queries, data migration scripts).
 * <p>
 * Encapsulates Wire Format V1 blob parsing, DEK lookup by namespace + dekVersion,
 * and type deserialization.
 * <p>
 * Payload detection and extraction is delegated to {@link StorageAdapter}.
 */
@Slf4j
public class FieldCryptoService {

    private final EntityMetadataCache metadataCache;
    private final TypeDeserializer typeDeserializer;
    private final KeyVaultService keyVaultService;
    private final StorageAdapter storageAdapter;
    private final DocumentAccessor documentAccessor;
    private final StructuredValueCodec structuredValueCodec;

    public FieldCryptoService(EntityMetadataCache metadataCache,
                              TypeDeserializer typeDeserializer,
                              KeyVaultService keyVaultService,
                              StorageAdapter storageAdapter,
                              DocumentAccessor documentAccessor,
                              StructuredValueCodec structuredValueCodec) {
        this.metadataCache = metadataCache;
        this.typeDeserializer = typeDeserializer;
        this.keyVaultService = keyVaultService;
        this.storageAdapter = storageAdapter;
        this.documentAccessor = documentAccessor;
        this.structuredValueCodec = structuredValueCodec;
    }

    /**
     * Decrypt all @Encrypted fields in the given raw document for the specified entity class.
     * Modifies the document in-place and returns the same reference.
     */
    public Object decryptDocument(Object rawDocument, Class<?> entityClass) {
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
    private void decryptField(Object document, EncryptedFieldMetadata meta, int depth) {
        PathSegmentType pathType = meta.pathTypes().get(depth);
        String segment = meta.path().get(depth);
        boolean isLeaf = depth == meta.path().size() - 1;

        if (pathType == PathSegmentType.FIELD) {
            Object raw = getFieldValue(document, segment);
            if (raw == null) {
                return;
            }
            if (isLeaf) {
                Object value = decryptPayload(raw, meta);
                if (value != null) {
                    setFieldValue(document, segment, value);
                }
                return;
            }
            if (isDocumentLike(raw)) {
                decryptField(raw, meta, depth + 1);
            }
            return;
        }

        if (pathType == PathSegmentType.LIST_ITER) {
            if (isLeaf && meta.wholeObject()) {
                Object raw = getFieldValue(document, segment);
                Object value = decryptPayload(raw, meta);
                if (value != null) {
                    setFieldValue(document, segment, value);
                }
                return;
            }

            Object rawArray = getFieldValue(document, segment);
            if (!(rawArray instanceof List<?> array)) {
                return;
            }

            for (int i = 0; i < array.size(); i++) {
                Object raw = array.get(i);
                if (isLeaf) {
                    Object value = decryptPayload(raw, meta);
                    if (value != null) {
                        ((List<Object>) array).set(i, value);
                    }
                } else if (isDocumentLike(raw)) {
                    decryptField(raw, meta, depth + 1);
                }
            }
            return;
        }

        if (pathType == PathSegmentType.MAP_ITER) {
            if (isLeaf && meta.wholeObject()) {
                Object raw = getFieldValue(document, segment);
                Object value = decryptPayload(raw, meta);
                if (value != null) {
                    setFieldValue(document, segment, value);
                }
                return;
            }

            Object rawMap = getFieldValue(document, segment);
            if (!isDocumentLike(rawMap)) {
                return;
            }
            // For map iteration, we iterate over the document's keys via DocumentAccessor
            Iterable<Map.Entry<String, Object>> entries = documentAccessor.asMap(rawMap);
            if (entries != null) {
                for (Map.Entry<String, Object> entry : entries) {
                    Object raw = entry.getValue();
                    if (isLeaf) {
                        Object value = decryptPayload(raw, meta);
                        if (value != null) {
                            entry.setValue(value);
                        }
                    } else if (isDocumentLike(raw)) {
                        decryptField(raw, meta, depth + 1);
                    }
                }
            }
        }
    }

    private Object decryptPayload(Object raw, EncryptedFieldMetadata meta) {
        if (!storageAdapter.isEncryptedPayload(raw)) {
            return null;
        }

        String blob = storageAdapter.extractBlob(raw);
        String typeMarker = storageAdapter.extractTypeMarker(raw);

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
        try {
            return structuredValueCodec.decode(plaintext, typeMarker);
        } catch (RuntimeException ex) {
            throw new DecryptionException("Failed to decode structured payload for type marker: " + typeMarker, ex);
        }
    }

    // ===== Document field access helpers (delegated to DocumentAccessor) =====

    private Object getFieldValue(Object document, String field) {
        return documentAccessor.getField(document, field);
    }

    private void setFieldValue(Object document, String field, Object value) {
        documentAccessor.setField(document, field, value);
    }

    private boolean isDocumentLike(Object value) {
        return documentAccessor.isDocumentLike(value);
    }
}
