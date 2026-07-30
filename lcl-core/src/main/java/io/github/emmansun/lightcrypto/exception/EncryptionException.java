package io.github.emmansun.lightcrypto.exception;

/**
 * Raised when encryption of data fails (encrypt-path failure).
 *
 * @since 1.0.0
 */
public class EncryptionException extends CryptoException {

    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
