package io.github.emmansun.lightcrypto.exception;

/**
 * Base runtime exception for cryptographic operations across LCL modules.
 */
public class CryptoException extends RuntimeException {
    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
