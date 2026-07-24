package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.spi.StorageAdapter;
import org.bson.Document;

/**
 * MongoDB implementation of {@link StorageAdapter} using BSON {@link Document}.
 *
 * <p>Encrypted field payload format:
 * <pre>
 * {
 *   "c": "Base64URL-wire-format-blob",
 *   "_e": 1,
 *   "_t": "STR",
 *   "b": "blind-index-value"  // optional
 * }
 * </pre>
 *
 * <p>This implementation is stateless and thread-safe.
 *
 * @since 1.0.0
 */
public class MongoStorageAdapter implements StorageAdapter {

    private static final String FIELD_CIPHERTEXT = "c";
    private static final String FIELD_ENCRYPTED_MARKER = "_e";
    private static final String FIELD_TYPE_MARKER = "_t";
    private static final String FIELD_BLIND_INDEX = "b";
    private static final int ENCRYPTED_MARKER_VALUE = 1;

    @Override
    public Object buildEncryptedPayload(String blob, String typeMarker, String blindIndex) {
        Document subDoc = new Document();
        subDoc.put(FIELD_CIPHERTEXT, blob);
        subDoc.put(FIELD_ENCRYPTED_MARKER, ENCRYPTED_MARKER_VALUE);
        subDoc.put(FIELD_TYPE_MARKER, typeMarker);
        if (blindIndex != null) {
            subDoc.put(FIELD_BLIND_INDEX, blindIndex);
        }
        return subDoc;
    }

    @Override
    public String extractBlob(Object payload) {
        if (payload instanceof Document doc) {
            return doc.getString(FIELD_CIPHERTEXT);
        }
        return null;
    }

    @Override
    public String extractTypeMarker(Object payload) {
        if (payload instanceof Document doc) {
            return doc.getString(FIELD_TYPE_MARKER);
        }
        return null;
    }

    @Override
    public String extractBlindIndex(Object payload) {
        if (payload instanceof Document doc) {
            return doc.getString(FIELD_BLIND_INDEX);
        }
        return null;
    }

    @Override
    public boolean isEncryptedPayload(Object value) {
        if (value instanceof Document doc) {
            Integer marker = doc.getInteger(FIELD_ENCRYPTED_MARKER);
            return marker != null && marker == ENCRYPTED_MARKER_VALUE;
        }
        return false;
    }
}
