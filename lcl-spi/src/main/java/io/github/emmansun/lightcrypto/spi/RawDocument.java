package io.github.emmansun.lightcrypto.spi;

import java.util.Map;

/**
 * A raw document representation for batch re-encryption operations.
 *
 * <p>Contains the document identifier, all field values (including encrypted payloads
 * in their raw stored form), and a concurrency token for CAS-based replacement.
 *
 * <p>The {@code fields} map contains field names as keys and their raw stored values
 * (encrypted payloads are NOT decrypted — the re-encryption engine handles decryption).
 *
 * @param id               the unique document identifier (e.g., MongoDB {@code _id})
 * @param fields           mutable map of field name to raw stored value
 * @param concurrencyToken the token used for CAS replacement (e.g., {@code updatedAt} timestamp)
 * @since 1.1.0
 */
public record RawDocument(
        String id,
        Map<String, Object> fields,
        Object concurrencyToken
) {
}
