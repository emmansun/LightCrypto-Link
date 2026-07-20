package io.github.emmansun.lightcrypto.spi;

import java.util.Optional;

/**
 * Abstraction for key vault persistence across different storage backends.
 *
 * <p>Phase 2 will provide concrete implementations for MongoDB, MySQL, PostgreSQL, and etcd.
 * In Phase 1, this interface serves as a design placeholder to establish the SPI boundary.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Persisting vault documents (DEK/HMAC key pairs wrapped by CMK)</li>
 *   <li>Loading vault documents by namespace</li>
 *   <li>Handling concurrent initialization (insert-if-absent semantics)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public interface VaultStore {

    /**
     * Persists a vault document. If a document with the same namespace already exists,
     * the implementation SHALL update it (upsert semantics).
     *
     * @param doc the vault document to save
     */
    void save(VaultDocument doc);

    /**
     * Loads a vault document by its canonical namespace.
     *
     * @param namespace the canonical namespace (e.g., "default.default.User")
     * @return the vault document, or empty if not found
     */
    Optional<VaultDocument> load(String namespace);

    /**
     * Checks whether a vault document exists for the given namespace.
     *
     * @param namespace the canonical namespace
     * @return true if a vault document exists
     */
    boolean exists(String namespace);
}
