package io.github.emmansun.lightcrypto.model;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Map;

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
        String effectiveFieldName,
        Namespace namespace
) {

        private static final Map<SymmetricAlgorithm, AlgorithmId> ALGORITHM_MAP = Map.of(
                SymmetricAlgorithm.AES_256_GCM, AlgorithmId.AES_256_GCM,
                SymmetricAlgorithm.AES_256_CBC, AlgorithmId.AES_256_CBC,
                SymmetricAlgorithm.SM4_GCM, AlgorithmId.SM4_GCM,
                SymmetricAlgorithm.SM4_CBC, AlgorithmId.SM4_CBC
        );

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

        /**
         * Returns the lcl-core AlgorithmId corresponding to this field's SymmetricAlgorithm.
         */
        public AlgorithmId algorithmId() {
                AlgorithmId id = ALGORITHM_MAP.get(algorithm);
                if (id == null) {
                        throw new IllegalStateException("Cannot map algorithm: " + algorithm);
                }
                return id;
        }
}
