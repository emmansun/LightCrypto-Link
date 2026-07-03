package io.github.emmansun.lightcrypto.exception;

/**
 * Fatal crypto exception — thrown when key integrity verification fails,
 * preventing the application from starting.
 */
public class FatalCryptoException extends RuntimeException {
    public FatalCryptoException(String message) {
        super(message);
    }

    public FatalCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
