package io.github.emmansun.lightcrypto.exception;

/**
 * Raised when GCM authentication tag or CBC padding verification fails.
 * <p>
 * This is a subtype of {@link DecryptionException} because it represents
 * a genuine decryption failure (as opposed to payload corruption which
 * occurs before decryption begins).
 *
 * @since 1.0.0
 */
public class CryptoAuthenticationException extends DecryptionException {

    private final String namespace;
    private final int dekVersion;
    private final String algorithm;

    /**
     * @param message    human-readable description
     * @param cause      the underlying cause (e.g. AEADBadTagException, BadPaddingException)
     * @param namespace  the namespace being decrypted (nullable)
     * @param dekVersion the DEK version used for decryption
     * @param algorithm  the algorithm name (e.g. "AES_256_GCM")
     */
    public CryptoAuthenticationException(String message, Throwable cause,
                                         String namespace, int dekVersion, String algorithm) {
        super(message, cause);
        this.namespace = namespace;
        this.dekVersion = dekVersion;
        this.algorithm = algorithm;
    }

    /**
     * Returns the namespace being decrypted (nullable).
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the DEK version used for decryption.
     */
    public int getDekVersion() {
        return dekVersion;
    }

    /**
     * Returns the algorithm name (e.g. "AES_256_GCM").
     */
    public String getAlgorithm() {
        return algorithm;
    }
}
