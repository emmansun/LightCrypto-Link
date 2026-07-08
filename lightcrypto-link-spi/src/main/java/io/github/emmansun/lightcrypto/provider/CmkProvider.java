package io.github.emmansun.lightcrypto.provider;

import io.github.emmansun.lightcrypto.model.GeneratedKey;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.util.CryptoUtils;

/**
 * CMK Provider SPI — wraps and unwraps data keys (for example DEK) with a CMK.
 */
public interface CmkProvider {

    /**
     * Get the unique provider identifier.
     */
    String getProviderId();

    /**
     * Get a non-secret public reference for the CMK used by this provider.
     */
    String getPublicReference();

    /**
     * Wrap raw key material using the CMK.
     */
    WrappedKey wrap(byte[] plaintextKey);

    /**
     * Unwrap a previously wrapped key using the CMK.
     */
    byte[] unwrap(WrappedKey wrappedKey);

    /**
     * Generate a new random symmetric key of the specified length, wrap it with the CMK, and return both.
     *
     * @param lengthInBytes the length of the raw symmetric key in bytes (e.g., 32 for AES-256)
     * @return a GeneratedKey containing the raw key and its wrapped representation
     */
    default GeneratedKey generateKey(int lengthInBytes) {
        byte[] rawKey = CryptoUtils.generateRandomBytes(lengthInBytes);
        WrappedKey wrappedKey = wrap(rawKey);
        return new GeneratedKey(rawKey, wrappedKey);
    }
}
