package io.github.emmansun.lightcrypto.spi;

import java.util.Map;

/**
 * Abstraction for field-level access on document-like objects.
 *
 * <p>Decouples the starter from any specific document type (BSON Document, Map, etc.).
 * Implementations provide read/write access to fields within structured documents
 * used during encryption/decryption operations.
 *
 * <p>Implementations MUST be stateless and thread-safe.
 *
 * @since 1.0.0
 */
public interface DocumentAccessor {

    /**
     * Read a field value from a document-like object.
     *
     * @param document the document-like object
     * @param field    the field name
     * @return the field value, or null if absent or document is null
     */
    Object getField(Object document, String field);

    /**
     * Write a field value into a document-like object (in-place modification).
     *
     * @param document the document-like object
     * @param field    the field name
     * @param value    the value to set
     */
    void setField(Object document, String field, Object value);

    /**
     * Check if a value is a nested document-like object (vs a scalar).
     *
     * @param value the value to check
     * @return true if the value is document-like and can be traversed
     */
    boolean isDocumentLike(Object value);

    /**
     * Adapt a value as an iterable list for element-wise traversal.
     *
     * @param value the value (typically a List or array)
     * @return an iterable over the elements, or null if not adaptable
     */
    Iterable<?> asList(Object value);

    /**
     * Adapt a value as key-value pairs for map-wise traversal.
     *
     * @param value the value (typically a Document or Map)
     * @return an iterable over the entries, or null if not adaptable
     */
    Iterable<Map.Entry<String, Object>> asMap(Object value);
}
