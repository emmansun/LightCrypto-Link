package io.github.emmansun.lightcrypto.exception;

/**
 * Raised when Wire Format parsing fails (pre-decrypt stage).
 * <p>
 * This exception is NOT a {@link DecryptionException} because it occurs
 * before decryption begins — the input data is structurally corrupt.
 *
 * @since 1.0.0
 */
public class PayloadCorruptionException extends CryptoException {

    private final String namespace;
    private final int rawLength;

    /**
     * @param message   human-readable description of the corruption
     * @param namespace the namespace extracted so far (nullable if not yet parsed)
     * @param rawLength the byte length of the raw blob (-1 if unknown)
     */
    public PayloadCorruptionException(String message, String namespace, int rawLength) {
        super(message);
        this.namespace = namespace;
        this.rawLength = rawLength;
    }

    /**
     * @param message   human-readable description of the corruption
     * @param cause     the underlying cause
     * @param namespace the namespace extracted so far (nullable if not yet parsed)
     * @param rawLength the byte length of the raw blob (-1 if unknown)
     */
    public PayloadCorruptionException(String message, Throwable cause, String namespace, int rawLength) {
        super(message, cause);
        this.namespace = namespace;
        this.rawLength = rawLength;
    }

    /**
     * Returns the namespace extracted before the failure (nullable).
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the byte length of the raw blob that failed to parse (-1 if unknown).
     */
    public int getRawLength() {
        return rawLength;
    }
}
