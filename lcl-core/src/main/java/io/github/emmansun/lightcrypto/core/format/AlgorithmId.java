package io.github.emmansun.lightcrypto.core.format;

/**
 * Registry of supported symmetric encryption algorithm identifiers for Wire Format V1.
 *
 * <p>Each algorithm has a unique 1-byte identifier used in the Wire Format blob header.
 *
 * @since 1.0.0
 */
public enum AlgorithmId {

    AES_256_GCM((byte) 0x01, 12, 32),
    AES_256_CBC((byte) 0x02, 16, 32),
    SM4_GCM((byte) 0x03, 12, 16),
    SM4_CBC((byte) 0x04, 16, 16);

    private final byte id;
    private final int ivLength;
    private final int keyLength;

    AlgorithmId(byte id, int ivLength, int keyLength) {
        this.id = id;
        this.ivLength = ivLength;
        this.keyLength = keyLength;
    }

    /**
     * Returns the 1-byte wire format identifier.
     */
    public byte id() {
        return id;
    }

    /**
     * Returns the IV length in bytes for this algorithm.
     */
    public int ivLength() {
        return ivLength;
    }

    /**
     * Returns the key length in bytes for this algorithm.
     */
    public int keyLength() {
        return keyLength;
    }

    /**
     * Returns whether this algorithm uses GCM mode (authenticated encryption).
     */
    public boolean isGcm() {
        return this == AES_256_GCM || this == SM4_GCM;
    }

    /**
     * Looks up an AlgorithmId by its byte identifier.
     *
     * @param id the byte identifier
     * @return the corresponding AlgorithmId
     * @throws IllegalArgumentException if the id is not recognized
     */
    public static AlgorithmId fromByte(byte id) {
        for (AlgorithmId alg : values()) {
            if (alg.id == id) {
                return alg;
            }
        }
        throw new IllegalArgumentException(
                String.format("unknown algorithm ID: 0x%02X", id & 0xFF));
    }
}
