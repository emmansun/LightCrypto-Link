package io.github.emmansun.lightcrypto.model;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;

import java.lang.invoke.MethodHandle;

/**
 * Encrypted field metadata, generated and cached by EntityMetadataCache during scanning.
 * <p>
 * The {@code getter} is a pre-bound {@link MethodHandle} that replaces reflective
 * {@code Field.get()} on the hot path, achieving near-direct-access performance.
 */
public record EncryptedFieldMetadata(
        MethodHandle getter,
        String fieldName,
        Class<?> javaType,
        SymmetricAlgorithm algorithm,
        boolean blindIndex,
        String effectiveFieldName
) {
}
