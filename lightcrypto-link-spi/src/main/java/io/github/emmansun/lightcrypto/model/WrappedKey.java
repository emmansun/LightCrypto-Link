package io.github.emmansun.lightcrypto.model;

import java.util.Map;

/**
 * Wrapped key container produced by a CMK provider.
 *
 * @param ciphertext wrapped ciphertext bytes
 * @param algorithm wrapping algorithm identifier
 */
public record WrappedKey(byte[] ciphertext, String algorithm, Map<String, String> metadata) {
    public WrappedKey(byte[] ciphertext, String algorithm) {
        this(ciphertext, algorithm, Map.of());
    }

    /**
     * Constructs a wrapped key with the specified ciphertext, algorithm, and metadata.
     *
     * @param ciphertext the wrapped ciphertext bytes
     * @param algorithm the wrapping algorithm identifier
     * @param metadata additional metadata associated with the wrapped key
     */
    public WrappedKey {
        if (metadata == null) {
            metadata = Map.of();
        }
        metadata = Map.copyOf(metadata); 
    }    
}
