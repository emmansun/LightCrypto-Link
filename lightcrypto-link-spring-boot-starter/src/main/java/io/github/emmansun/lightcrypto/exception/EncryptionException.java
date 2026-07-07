package io.github.emmansun.lightcrypto.exception;

/**
 * Raised when encryption of field values fails.
 */
public class EncryptionException extends CryptoException {
    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
