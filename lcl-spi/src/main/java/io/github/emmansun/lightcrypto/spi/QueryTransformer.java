package io.github.emmansun.lightcrypto.spi;

/**
 * Abstraction for query rewrite across different databases.
 *
 * <p>Phase 2 will provide concrete implementations for:
 * <ul>
 *   <li>MongoDB: rewrite Spring Data derived queries to target blind index fields ({@code field.b})</li>
 *   <li>JPA/SQL: rewrite predicates to target encrypted column blind index columns</li>
 * </ul>
 *
 * <p>In Phase 1, this interface serves as a design placeholder to establish the SPI boundary.
 * The Spring Boot starter currently handles MongoDB query rewrite via {@code CryptoMongoQueryCreator}.
 *
 * @since 1.0.0
 */
public interface QueryTransformer {
    // Phase 2: methods for transforming query criteria from plaintext field references
    // to blind-index-based lookups in the target database dialect.
}
