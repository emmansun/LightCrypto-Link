package io.github.emmansun.lightcrypto.spi;

import java.time.Duration;

/**
 * Options for batch document scanning during re-encryption.
 *
 * @param collectionHint the target collection/table name hint (adapter-specific interpretation)
 * @param batchSize      number of documents to fetch per batch (default 500)
 * @param resumeAfter    cursor state to resume from (null for fresh scan)
 * @param maxScanTime    maximum time allowed for the full scan (null for unlimited)
 * @since 1.1.0
 */
public record ScanOptions(
        String collectionHint,
        int batchSize,
        String resumeAfter,
        Duration maxScanTime
) {

    /** Default batch size for scanning operations. */
    public static final int DEFAULT_BATCH_SIZE = 500;

    /**
     * Creates scan options with default batch size and no resume/timeout.
     *
     * @param collectionHint the target collection hint
     * @return scan options with defaults
     */
    public static ScanOptions of(String collectionHint) {
        return new ScanOptions(collectionHint, DEFAULT_BATCH_SIZE, null, null);
    }

    /**
     * Creates scan options with a specified batch size.
     *
     * @param collectionHint the target collection hint
     * @param batchSize      the batch size
     * @return scan options
     */
    public static ScanOptions of(String collectionHint, int batchSize) {
        return new ScanOptions(collectionHint, batchSize, null, null);
    }
}
