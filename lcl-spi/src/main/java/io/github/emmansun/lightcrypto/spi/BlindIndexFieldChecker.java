package io.github.emmansun.lightcrypto.spi;

/**
 * Functional interface for checking if a field supports blind index queries.
 *
 * <p>Used by {@link QueryTransformer} implementations to determine whether
 * a field can be queried via blind index without coupling to specific metadata caches.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface BlindIndexFieldChecker {

    /**
     * Determines whether a field supports blind-index query rewrite.
     *
     * @param field      the field name to check
     * @param entityType the entity class containing the field
     * @return true if the field has blind index enabled
     */
    boolean hasBlindIndex(String field, Class<?> entityType);
}
