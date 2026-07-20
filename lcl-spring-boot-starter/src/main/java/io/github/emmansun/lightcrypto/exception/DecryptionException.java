package io.github.emmansun.lightcrypto.exception;

/**
 * Raised when decryption of field values fails.
 */
public class DecryptionException extends CryptoException {
    public DecryptionException(String message) {
        super(message);
    }

    public DecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
