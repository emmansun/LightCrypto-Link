package io.github.emmansun.lightcrypto.exception;

/**
 * Raised when vault or DEK version cannot be resolved on the decrypt path.
 * <p>
 * Unlike the encrypt path (which auto-initializes vaults), the decrypt path
 * is strictly read-only — a missing vault or version indicates a configuration
 * or data integrity problem.
 *
 * @since 1.0.0
 */
public class KeyResolutionException extends KeyManagementException {

    private final String namespace;
    private final int dekVersion;

    /**
     * @param message    human-readable description
     * @param namespace  the canonical namespace
     * @param dekVersion the requested DEK version (0 if vault-level miss)
     */
    public KeyResolutionException(String message, String namespace, int dekVersion) {
        super(message);
        this.namespace = namespace;
        this.dekVersion = dekVersion;
    }

    /**
     * @param message    human-readable description
     * @param cause      the underlying cause
     * @param namespace  the canonical namespace
     * @param dekVersion the requested DEK version (0 if vault-level miss)
     */
    public KeyResolutionException(String message, Throwable cause, String namespace, int dekVersion) {
        super(message, cause);
        this.namespace = namespace;
        this.dekVersion = dekVersion;
    }

    /**
     * Returns the canonical namespace.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the requested DEK version (0 if vault-level miss).
     */
    public int getDekVersion() {
        return dekVersion;
    }
}
