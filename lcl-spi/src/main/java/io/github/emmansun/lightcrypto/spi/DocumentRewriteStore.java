package io.github.emmansun.lightcrypto.spi;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Adapter-agnostic contract for batch document scanning, atomic replacement,
 * and checkpoint persistence during DEK re-encryption.
 *
 * <p>Implementations provide database-specific optimizations (cursor types, bulk writes,
 * read preferences) while the re-encryption engine depends only on this SPI.
 *
 * <p>Implementations MUST be thread-safe.
 *
 * @since 1.1.0
 */
public interface DocumentRewriteStore {

    /**
     * Scans documents in stable order without decrypting any fields.
     *
     * <p>The returned iterator yields {@link RawDocument} instances with encrypted
     * payloads in their raw stored form. The engine is responsible for decryption
     * and re-encryption.
     *
     * @param options scan configuration (collection hint, batch size, resume point)
     * @return a closeable iterator over raw documents in stable {@code _id} order
     */
    CloseableIterator<RawDocument> scan(ScanOptions options);

    /**
     * Atomically replaces a document using per-field kid CAS.
     *
     * <p>The replacement SHALL succeed ONLY if each encrypted field's current {@code _k}
     * value matches the kid snapshot in {@link RawDocument#fieldKids()}. If any field was
     * concurrently re-encrypted (kid changed), the replace SHALL return {@code false}.
     * If {@code fieldKids} is empty, an {@code _id}-only filter is used (no CAS protection).
     *
     * @param document the document with updated field values
     * @return true if the replacement succeeded, false on CAS conflict
     */
    boolean replace(RawDocument document);

    /**
     * Replaces multiple documents using database-specific bulk operations.
     *
     * <p>Implementations MAY use bulk write operations for throughput optimization.
     * Each document is still subject to CAS protection individually.
     *
     * @param documents the documents to replace
     * @return the count of successfully replaced documents
     */
    int replaceBatch(List<RawDocument> documents);

    /**
     * Persists a checkpoint (cursor position) for resumability.
     *
     * <p>The checkpoint is an opaque string (typically the last processed document ID)
     * that enables resume after interruption without re-scanning completed documents.
     *
     * @param taskId      unique identifier for the re-encryption task
     * @param cursorState the cursor state to persist
     */
    void saveCheckpoint(String taskId, String cursorState);

    /**
     * Loads a previously saved checkpoint for the given task.
     *
     * @param taskId unique identifier for the re-encryption task
     * @return the cursor state, or empty if no checkpoint exists
     */
    Optional<String> loadCheckpoint(String taskId);

    /**
     * An iterator that holds external resources (e.g., database cursors) that
     * must be released when iteration is complete.
     *
     * @param <T> the element type
     */
    interface CloseableIterator<T> extends Iterator<T>, AutoCloseable {
        @Override
        void close();
    }
}
