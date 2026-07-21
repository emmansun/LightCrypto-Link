package io.github.emmansun.lightcrypto.spi;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction for key vault persistence across different storage backends.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Persisting vault documents (DEK/HMAC key pairs wrapped by CMK)</li>
 *   <li>Loading vault documents by namespace</li>
 *   <li>Handling concurrent initialization (insert-if-absent semantics)</li>
 *   <li>Optimistic-locking rotation (CAS on version field)</li>
 *   <li>Bulk loading for startup preloading</li>
 * </ul>
 *
 * <p>Implementations MUST be thread-safe for concurrent access from multiple
 * threads without external synchronization.
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
     * @param namespace the canonical namespace (e.g., "default.default.User#phone")
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

    /**
     * Persists a vault document with optimistic-locking (CAS on {@code version} field).
     *
     * <p>The implementation SHALL verify that the stored document's version equals
     * {@code updatedDoc.version() - 1} before applying the update. If the version
     * does not match (concurrent modification), an {@link io.github.emmansun.lightcrypto.exception.OptimisticLockException}
     * SHALL be thrown and the stored document SHALL NOT be modified.
     *
     * @param updatedDoc the updated vault document with incremented version
     * @return the persisted vault document
     * @throws io.github.emmansun.lightcrypto.exception.OptimisticLockException if the stored version does not match expected
     */
    VaultDocument rotate(VaultDocument updatedDoc);

    /**
     * Loads all vault documents (for startup preloading).
     *
     * @return a list of all vault documents in the store
     */
    List<VaultDocument> loadAll();
}
