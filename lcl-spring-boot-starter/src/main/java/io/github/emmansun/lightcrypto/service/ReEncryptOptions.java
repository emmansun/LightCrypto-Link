package io.github.emmansun.lightcrypto.service;

/**
 * Options for DEK re-encryption operations.
 *
 * @param entityClass        the entity class to re-encrypt (null for reEncryptAll)
 * @param batchSize          number of documents to process per batch (default 500)
 * @param taskId             unique identifier for checkpoint/resume (auto-generated if null)
 * @param dryRun             if true, scan and count but do not modify documents
 * @param checkpointInterval save checkpoint every N batches (default 10)
 * @since 1.1.0
 */
public record ReEncryptOptions(
        Class<?> entityClass,
        int batchSize,
        String taskId,
        boolean dryRun,
        int checkpointInterval
) {

    /** Default batch size for re-encryption operations. */
    public static final int DEFAULT_BATCH_SIZE = 500;

    /** Default checkpoint interval (every N batches). */
    public static final int DEFAULT_CHECKPOINT_INTERVAL = 10;

    /**
     * Creates options with defaults for the given entity class.
     *
     * @param entityClass the entity class to re-encrypt
     * @return options with default settings
     */
    public static ReEncryptOptions forEntity(Class<?> entityClass) {
        return new ReEncryptOptions(entityClass, DEFAULT_BATCH_SIZE, null, false, DEFAULT_CHECKPOINT_INTERVAL);
    }

    /**
     * Creates options for reEncryptAll (all registered entity classes).
     *
     * @return options with default settings and null entityClass
     */
    public static ReEncryptOptions forAll() {
        return new ReEncryptOptions(null, DEFAULT_BATCH_SIZE, null, false, DEFAULT_CHECKPOINT_INTERVAL);
    }

    /**
     * Returns a copy with the specified batch size.
     */
    public ReEncryptOptions withBatchSize(int batchSize) {
        return new ReEncryptOptions(entityClass, batchSize, taskId, dryRun, checkpointInterval);
    }

    /**
     * Returns a copy with the specified task ID.
     */
    public ReEncryptOptions withTaskId(String taskId) {
        return new ReEncryptOptions(entityClass, batchSize, taskId, dryRun, checkpointInterval);
    }

    /**
     * Returns a copy with dryRun enabled.
     */
    public ReEncryptOptions withDryRun(boolean dryRun) {
        return new ReEncryptOptions(entityClass, batchSize, taskId, dryRun, checkpointInterval);
    }
}
