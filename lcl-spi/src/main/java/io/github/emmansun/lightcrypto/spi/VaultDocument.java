package io.github.emmansun.lightcrypto.spi;

import java.time.Instant;
import java.util.List;

/**
 * Represents a key vault document for a specific namespace.
 *
 * <p>Each namespace has its own vault document containing one or more versioned
 * DEK/HMAC key pairs wrapped by the CMK provider.
 *
 * @param namespace the canonical namespace this vault serves (e.g., "default.default.User")
 * @param keys      the list of key entries (versioned, wrapped)
 * @param activeKid the kid of the currently active key entry
 * @since 1.0.0
 */
public record VaultDocument(
        String namespace,
        List<KeyEntry> keys,
        String activeKid
) {

    /**
     * A single versioned key entry within a vault document.
     *
     * @param kid        key identifier in format {@code v{n}-{8hexchars}}
     * @param status     lifecycle status of this key
     * @param wrappedDek the DEK wrapped by CMK
     * @param wrappedHmac the HMAC key wrapped by CMK
     * @param dekKcv     key check value for the DEK (3 bytes, hex-encoded)
     * @param hmacKcv    key check value for the HMAC key (3 bytes, hex-encoded)
     * @param binding    binding hash between DEK and HMAC key
     * @param createdAt  creation timestamp
     */
    public record KeyEntry(
            String kid,
            KeyStatus status,
            byte[] wrappedDek,
            byte[] wrappedHmac,
            String dekKcv,
            String hmacKcv,
            String binding,
            Instant createdAt
    ) {
    }

    /**
     * Lifecycle status of a key entry.
     */
    public enum KeyStatus {
        /** Key is currently active for encryption. */
        ACTIVE,
        /** Key has been rotated; still usable for decryption. */
        ROTATED,
        /** Key has been revoked; not usable. */
        REVOKED
    }
}
