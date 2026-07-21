package io.github.emmansun.lightcrypto.spi;

/**
 * Handler for decrypting entity fields after retrieval.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Identifying encrypted payloads in the document</li>
 *   <li>Extracting blobs via StorageAdapter</li>
 *   <li>Decrypting using the appropriate DEK</li>
 *   <li>Deserializing to original field types</li>
 * </ul>
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface DecryptHandler {

    /**
     * Decrypts all encrypted fields in the given document for the specified entity class.
     *
     * @param document    the raw document to decrypt (modified in-place)
     * @param entityClass the entity class
     */
    void decrypt(Object document, Class<?> entityClass);
}
