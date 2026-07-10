package io.github.emmansun.lightcrypto.exception;

/**
 * Unsupported field type exception — thrown during metadata scanning when an
 * '@Encrypted' field has a Java type not in the supported list.
 */
public class UnsupportedTypeException extends CryptoException {
    public UnsupportedTypeException(String message) {
        super(message);
    }
}
