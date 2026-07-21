package io.github.emmansun.lightcrypto.spi;

/**
 * Abstraction for database-specific encrypted field payload handling.
 *
 * <p>Implementations define how encrypted field components (blob, type marker, blind index)
 * are stored in and retrieved from the target database format:
 * <ul>
 *   <li>MongoDB: BSON sub-document {@code {c: String, _e: 1, _t: String, b?: String}}</li>
 *   <li>MySQL/PostgreSQL: JSON column or dedicated encrypted column type</li>
 *   <li>Generic: Base64URL string in a VARCHAR/TEXT column</li>
 * </ul>
 *
 * <p>Implementations MUST be stateless and thread-safe. They SHALL NOT hold
 * references to database connections or sessions.
 *
 * <p>Round-trip invariant: given {@code payload = buildEncryptedPayload(blob, typeMarker, blindIndex)},
 * then {@code extractBlob(payload) == blob}, {@code extractTypeMarker(payload) == typeMarker},
 * and {@code extractBlindIndex(payload) == blindIndex}.
 *
 * @since 1.0.0
 */
public interface StorageAdapter {

    /**
     * Constructs a database-specific encrypted payload from components.
     *
     * @param blob       the Base64URL wire-format blob
     * @param typeMarker the type marker (e.g., "STR", "INT", "LDATE")
     * @param blindIndex the blind index value; may be null if not applicable
     * @return a database-specific payload object
     */
    Object buildEncryptedPayload(String blob, String typeMarker, String blindIndex);

    /**
     * Extracts the Base64URL wire-format blob from a payload.
     *
     * @param payload the encrypted payload object
     * @return the Base64URL wire-format blob string
     */
    String extractBlob(Object payload);

    /**
     * Extracts the type marker from a payload.
     *
     * @param payload the encrypted payload object
     * @return the type marker (e.g., "STR", "INT")
     */
    String extractTypeMarker(Object payload);

    /**
     * Extracts the blind index value from a payload.
     *
     * @param payload the encrypted payload object
     * @return the blind index value, or null if absent
     */
    String extractBlindIndex(Object payload);

    /**
     * Determines whether a raw field value is an encrypted payload (vs plaintext).
     *
     * @param value the raw field value to check; may be null
     * @return true if the value is an encrypted payload
     */
    boolean isEncryptedPayload(Object value);
}
