package io.github.emmansun.lightcrypto.provider;

import io.github.emmansun.lightcrypto.model.WrappedKey;

/**
 * CMK Provider SPI — wraps/unwraps data keys (e.g. DEK) using a CMK.
 * <p>
 * v1 implementation: {@link LocalSymmetricCmkProvider} (local symmetric CMK).
 * v2 will extend to remote providers such as Azure Key Vault and Alibaba Cloud KMS.
 * </p>
 */
public interface CmkProvider {

    /**
     * Get the unique provider identifier.
     */
    String getProviderId();

    /**
     * Get a non-secret public reference for the CMK used by this provider.
     * <p>
     * This value is safe to persist in metadata/audit records and MUST NOT
     * expose raw key material.
     * </p>
     */
    String getPublicReference();

    /**
     * Wrap (encrypt) raw key material using the CMK.
     *
     * @param plaintextKey raw key bytes
     * @return wrapped ciphertext + algorithm identifier
     */
    WrappedKey wrap(byte[] plaintextKey);

    /**
     * Unwrap (decrypt) a previously wrapped key using the CMK.
     *
     * @param wrappedKey the wrapped key
     * @return raw key bytes
     */
    byte[] unwrap(WrappedKey wrappedKey);
}
