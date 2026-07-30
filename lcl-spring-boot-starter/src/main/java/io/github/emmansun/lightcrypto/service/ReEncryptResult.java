package io.github.emmansun.lightcrypto.service;

/**
 * Result of a DEK re-encryption operation.
 *
 * @param namespace         the namespace that was re-encrypted
 * @param docsProcessed     number of documents successfully re-encrypted
 * @param docsSkipped       number of documents skipped (CAS conflict or already current)
 * @param docsFailed        number of documents that failed due to errors
 * @param fieldsReEncrypted total number of fields re-encrypted across all documents
 * @param durationMicros    total duration in microseconds
 * @since 1.1.0
 */
public record ReEncryptResult(
        String namespace,
        long docsProcessed,
        long docsSkipped,
        long docsFailed,
        long fieldsReEncrypted,
        long durationMicros
) {

    /**
     * Returns true if the operation completed without any failures.
     */
    public boolean success() {
        return docsFailed == 0;
    }

    /**
     * Returns the total number of documents scanned (processed + skipped + failed).
     */
    public long totalDocsScanned() {
        return docsProcessed + docsSkipped + docsFailed;
    }
}
