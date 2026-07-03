package io.github.emmansun.lightcrypto.exception;

/**
 * General-purpose exception for encryption/decryption operations.
 */
public class CryptoException extends RuntimeException {
    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
