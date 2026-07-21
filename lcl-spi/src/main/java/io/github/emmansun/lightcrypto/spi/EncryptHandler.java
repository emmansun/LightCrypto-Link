package io.github.emmansun.lightcrypto.spi;

/**
 * Handler for encrypting entity fields before persistence.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Identifying @Encrypted fields in the entity</li>
 *   <li>Serializing field values</li>
 *   <li>Encrypting using the appropriate DEK</li>
 *   <li>Building encrypted payloads via StorageAdapter</li>
 * </ul>
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface EncryptHandler {

    /**
     * Encrypts all @Encrypted fields in the given document for the specified entity.
     *
     * @param document    the raw document to encrypt (modified in-place)
     * @param entity      the source entity object
     * @param entityClass the entity class
     */
    void encrypt(Object document, Object entity, Class<?> entityClass);
}
