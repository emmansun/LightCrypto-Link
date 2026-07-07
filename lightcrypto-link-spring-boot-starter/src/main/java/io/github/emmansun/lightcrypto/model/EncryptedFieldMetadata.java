package io.github.emmansun.lightcrypto.model;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;

import java.lang.invoke.MethodHandle;
import java.util.List;

/**
 * Encrypted field metadata, generated and cached by EntityMetadataCache during scanning.
 * <p>
 * The {@code getter} is a pre-bound {@link MethodHandle} that replaces reflective
 * {@code Field.get()} on the hot path, achieving near-direct-access performance.
 */
public record EncryptedFieldMetadata(
                List<MethodHandle> accessors,
                List<String> path,
                List<PathSegmentType> pathTypes,
        Class<?> javaType,
        SymmetricAlgorithm algorithm,
        boolean blindIndex,
                boolean wholeObject,
        String effectiveFieldName
) {

        public String bsonFieldName() {
                return String.join(".", path);
        }

        public String leafFieldName() {
                return path.get(path.size() - 1);
        }

        public MethodHandle getter() {
                return accessors.get(accessors.size() - 1);
        }

        public String fieldName() {
                return leafFieldName();
        }

        public String blindIndexFieldName() {
                return effectiveFieldName == null || effectiveFieldName.isEmpty()
                                ? bsonFieldName()
                                : effectiveFieldName;
        }
}
