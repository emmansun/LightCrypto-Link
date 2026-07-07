package io.github.emmansun.lightcrypto.provider;

import io.github.emmansun.lightcrypto.model.WrappedKey;

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
}
