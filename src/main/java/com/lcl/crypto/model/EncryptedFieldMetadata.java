package com.lcl.crypto.model;

import com.lcl.crypto.annotation.SymmetricAlgorithm;

import java.lang.reflect.Field;

/**
 * Encrypted field metadata, generated and cached by EntityMetadataCache during scanning.
 */
public record EncryptedFieldMetadata(
        Field field,
        String fieldName,
        Class<?> javaType,
        SymmetricAlgorithm algorithm,
        boolean blindIndex,
        String effectiveFieldName
) {
}
