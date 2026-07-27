package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.service.TypeSerializer;

final class BlindIndexValueEncoder {

    private BlindIndexValueEncoder() {
    }

    static String computeBlindIndex(BlindIndexEngine engine,
                                    TypeSerializer typeSerializer,
                                    Namespace namespace,
                                    String fieldName,
                                    Object value) {
        if (value instanceof String str) {
            return engine.computeBlindIndex(namespace, fieldName, str);
        }
        byte[] serialized = typeSerializer.serialize(value);
        return engine.computeBlindIndex(namespace, fieldName, serialized);
    }
}