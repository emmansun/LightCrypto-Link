package io.github.emmansun.lightcrypto.exception;

/**
 * Grouping parent for decrypt-path failures.
 * <p>
 * Subtypes: {@link CryptoAuthenticationException} (GCM/CBC auth failure),
 * and starter-level SchemaDriftException (type deserialization failure).
 * <p>
 * Catching this class will also catch all decrypt-path subtypes polymorphically.
 *
 * @since 1.0.0
 */
public class DecryptionException extends CryptoException {

    public DecryptionException(String message) {
        super(message);
    }

    public DecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
