package io.github.emmansun.lightcrypto.exception;

/**
 * Raised when LCL configuration is invalid or inconsistent.
 */
public class ConfigurationException extends CryptoException {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
