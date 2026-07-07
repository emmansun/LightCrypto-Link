package io.github.emmansun.lightcrypto.model;

/**
 * Wrapped key container produced by a CMK provider.
 *
 * @param ciphertext wrapped ciphertext bytes
 * @param algorithm wrapping algorithm identifier
 */
public record WrappedKey(byte[] ciphertext, String algorithm) {
}
