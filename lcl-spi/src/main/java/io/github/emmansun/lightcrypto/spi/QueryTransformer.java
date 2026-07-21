package io.github.emmansun.lightcrypto.spi;

/**
 * Abstraction for blind-index query rewrite across different databases.
 *
 * <p>Implementations transform plaintext field references and query values into
 * blind-index-based lookups in the target database dialect:
 * <ul>
 *   <li>MongoDB: rewrite field to {@code field.b}, value to HMAC-based hash</li>
 *   <li>JPA/SQL: rewrite predicates to target blind index columns</li>
 * </ul>
 *
 * <p>Implementations MUST be thread-safe. They MAY hold a reference to
 * key management services for HMAC key retrieval but SHALL NOT cache query results.
 *
 * @since 1.0.0
 */
public interface QueryTransformer {

    /**
     * Transforms a plaintext field reference to the blind-index query target.
     *
     * <p>For example, MongoDB adapter returns {@code field + ".b"}.
     *
     * @param originalField the original plaintext field name
     * @return the rewritten field name targeting the blind index
     */
    String rewriteFieldName(String originalField);

    /**
     * Transforms a plaintext query value into the blind-index lookup value.
     *
     * <p>Delegates blind index computation to {@code BlindIndexEngine} using
     * the namespace-scoped HMAC key.
     *
     * @param plaintextValue the plaintext query value
     * @param namespace      the canonical namespace for key derivation
     * @return the blind-index hash value for lookup
     */
    Object rewriteQueryValue(Object plaintextValue, String namespace);

    /**
     * Determines whether a field supports blind-index query rewrite.
     *
     * @param field      the field name to check
     * @param entityType the entity class containing the field
     * @return true if the field has blind index enabled
     */
    boolean supportsField(String field, Class<?> entityType);
}
