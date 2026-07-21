package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.spi.DocumentAccessor;
import org.bson.Document;

import java.util.List;
import java.util.Map;

/**
 * BSON implementation of {@link DocumentAccessor}.
 *
 * <p>Operates on {@link org.bson.Document} instances, providing field-level
 * read/write access for encryption/decryption traversal.
 *
 * @since 1.0.0
 */
public class BsonDocumentAccessor implements DocumentAccessor {

    @Override
    public Object getField(Object document, String field) {
        if (document instanceof Document doc) {
            return doc.get(field);
        }
        return null;
    }

    @Override
    public void setField(Object document, String field, Object value) {
        if (document instanceof Document doc) {
            doc.put(field, value);
        }
    }

    @Override
    public boolean isDocumentLike(Object value) {
        return value instanceof Document;
    }

    @Override
    public Iterable<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterable<Map.Entry<String, Object>> asMap(Object value) {
        if (value instanceof Document doc) {
            return (Iterable<Map.Entry<String, Object>>) (Iterable<?>) doc.entrySet();
        }
        return null;
    }
}
