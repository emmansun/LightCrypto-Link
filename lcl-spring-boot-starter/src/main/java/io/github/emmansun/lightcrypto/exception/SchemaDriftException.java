package io.github.emmansun.lightcrypto.exception;

/**
 * Raised when decryption succeeds but type deserialization fails.
 * <p>
 * This indicates schema drift — the encrypted data was produced with a
 * different type expectation than the current entity model.
 *
 * @since 1.0.0
 */
public class SchemaDriftException extends DecryptionException {

    private final String namespace;
    private final String targetType;
    private final String fieldPath;

    /**
     * @param message    human-readable description
     * @param cause      the underlying deserialization failure
     * @param namespace  the canonical namespace (nullable)
     * @param targetType the expected Java type name
     * @param fieldPath  the dot-separated field path (nullable)
     */
    public SchemaDriftException(String message, Throwable cause,
                                String namespace, String targetType, String fieldPath) {
        super(message, cause);
        this.namespace = namespace;
        this.targetType = targetType;
        this.fieldPath = fieldPath;
    }

    /**
     * Returns the canonical namespace (nullable).
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the expected Java type name that failed deserialization.
     */
    public String getTargetType() {
        return targetType;
    }

    /**
     * Returns the dot-separated field path (nullable).
     */
    public String getFieldPath() {
        return fieldPath;
    }
}
