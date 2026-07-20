package io.github.emmansun.lightcrypto.spi;

/**
 * Abstraction for database-specific field storage formats.
 *
 * <p>Phase 2 will provide concrete implementations for:
 * <ul>
 *   <li>MongoDB: BSON sub-document {@code {c: Binary, b: String, _e: 1, _t: String, _k: String}}</li>
 *   <li>MySQL/PostgreSQL: JSON column or dedicated encrypted column type</li>
 *   <li>Generic: Base64URL string in a VARCHAR/TEXT column</li>
 * </ul>
 *
 * <p>In Phase 1, this interface serves as a design placeholder to establish the SPI boundary.
 * The Spring Boot starter currently handles MongoDB storage directly.
 *
 * @since 1.0.0
 */
public interface StorageAdapter {
    // Phase 2: methods for serializing/deserializing encrypted field payloads
    // to/from database-specific storage formats.
}
