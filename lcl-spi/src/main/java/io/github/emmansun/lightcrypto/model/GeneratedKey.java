package io.github.emmansun.lightcrypto.model;

/**
 * GeneratedKey is a container for a raw symmetric key and its wrapped representation.
 * @param rawKey the raw symmetric key bytes (e.g., 32 bytes for AES-256)
 * @param wrappedKey the wrapped key produced by a CMK provider
 */
public record GeneratedKey(byte[] rawKey, WrappedKey wrappedKey) {
}
