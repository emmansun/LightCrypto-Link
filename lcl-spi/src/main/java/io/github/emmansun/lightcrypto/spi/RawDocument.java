package io.github.emmansun.lightcrypto.spi;

import java.util.Map;

/**
 * A raw document representation for batch re-encryption operations.
 *
 * <p>Contains the document identifier, all field values (including encrypted payloads
 * in their raw stored form), and a per-field kid snapshot for CAS-based replacement.
 *
 * <p>The {@code fields} map contains field names as keys and their raw stored values
 * (encrypted payloads are NOT decrypted — the re-encryption engine handles decryption).
 *
 * <p>The {@code fieldKids} map carries per-field kid snapshots captured during scan.
 * Each entry maps an encrypted field path to the {@code _k} value found in its
 * encrypted sub-document. The rewrite store uses these entries to build CAS filters
 * (e.g., {@code "phone._k": "v1-kid"}). If a field has no {@code _k} sub-field
 * (legacy blob), it is simply absent from the map.
 *
 * @param id        the unique document identifier (e.g., MongoDB {@code _id})
 * @param fields    mutable map of field name to raw stored value
 * @param fieldKids per-field kid snapshot (field path → kid value); empty map means no CAS protection
 * @since 1.1.0
 */
public record RawDocument(
        String id,
        Map<String, Object> fields,
        Map<String, String> fieldKids
) {
}
