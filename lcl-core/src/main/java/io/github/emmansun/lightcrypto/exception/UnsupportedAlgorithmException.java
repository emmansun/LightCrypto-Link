package io.github.emmansun.lightcrypto.exception;

/**
 * Raised when an unknown algorithm ID is encountered or no encryptor is registered.
 *
 * @since 1.0.0
 */
public class UnsupportedAlgorithmException extends CryptoException {

    private final int algorithmId;
    private final String algorithmName;

    /**
     * @param message       human-readable description
     * @param algorithmId   the unknown algorithm byte value (unsigned)
     * @param algorithmName the algorithm name if known (nullable)
     */
    public UnsupportedAlgorithmException(String message, int algorithmId, String algorithmName) {
        super(message);
        this.algorithmId = algorithmId;
        this.algorithmName = algorithmName;
    }

    /**
     * Returns the unknown algorithm byte value (unsigned, 0-255).
     */
    public int getAlgorithmId() {
        return algorithmId;
    }

    /**
     * Returns the algorithm name if known (nullable).
     */
    public String getAlgorithmName() {
        return algorithmName;
    }
}
