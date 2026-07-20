package io.github.emmansun.lightcrypto.exception;

/**
 * Raised when DEK/CMK lookup, unwrap, rotation, or related key operations fail.
 */
public class KeyManagementException extends CryptoException {
    public KeyManagementException(String message) {
        super(message);
    }

    public KeyManagementException(String message, Throwable cause) {
        super(message, cause);
    }
}
