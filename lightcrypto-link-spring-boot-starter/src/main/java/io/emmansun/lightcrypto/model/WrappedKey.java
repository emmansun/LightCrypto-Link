package io.emmansun.lightcrypto.model;

/**
 * Wrapped key container produced by a CMK Provider.
 * The algorithm field enables self-describing unwrapping: even if the CMK is upgraded
 * from symmetric to asymmetric, legacy data can still be unwrapped with the old algorithm.
 *
 * @param ciphertext wrapped ciphertext (IV || GCM-ciphertext)
 * @param algorithm  wrapping algorithm identifier (e.g. "AES-256-GCM", "RSA-OAEP-256")
 */
public record WrappedKey(byte[] ciphertext, String algorithm) {
}
