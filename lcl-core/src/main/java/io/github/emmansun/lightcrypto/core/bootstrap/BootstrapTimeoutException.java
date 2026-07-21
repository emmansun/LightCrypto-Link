package io.github.emmansun.lightcrypto.core.bootstrap;

import io.github.emmansun.lightcrypto.exception.CryptoException;

/**
 * Thrown when the bootstrap sequence exceeds its configured timeout.
 *
 * @since 1.0.0
 */
public class BootstrapTimeoutException extends CryptoException {

    public BootstrapTimeoutException(String message) {
        super(message);
    }

    public BootstrapTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
