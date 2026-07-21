package io.github.emmansun.lightcrypto.spi;

/**
 * Functional interface for retrieving HMAC keys by namespace.
 *
 * <p>Used by {@link QueryTransformer} implementations to obtain namespace-scoped
 * HMAC keys for blind index computation without coupling to specific key management services.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface HmacKeyProvider {

    /**
     * Retrieves the active HMAC key for the given namespace.
     *
     * @param namespace the canonical namespace
     * @return the raw HMAC key bytes
     */
    byte[] getActiveHmacKey(String namespace);
}
