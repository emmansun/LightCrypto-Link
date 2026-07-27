package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.spi.QueryTransformer;
import io.github.emmansun.lightcrypto.service.TypeSerializer;

/**
 * Simple QueryTransformer for unit tests.
 * Mirrors the behavior of MongoQueryTransformer using a fixed HMAC key.
 */
public class TestQueryTransformer implements QueryTransformer {

    private final byte[] hmacKey;
    private final TypeSerializer typeSerializer;

    public TestQueryTransformer(byte[] hmacKey, TypeSerializer typeSerializer) {
        this.hmacKey = hmacKey;
        this.typeSerializer = typeSerializer;
    }

    @Override
    public String rewriteFieldName(String originalField) {
        return originalField + ".b";
    }

    @Override
    public Object rewriteQueryValue(Object plaintextValue, String namespace) {
        BlindIndexEngine engine = new BlindIndexEngine(hmacKey);
        Namespace ns = Namespace.parse(namespace);
        String fieldName = ns.field();
        return BlindIndexValueEncoder.computeBlindIndex(
                engine, typeSerializer, ns, fieldName, plaintextValue);
    }

    @Override
    public boolean supportsField(String field, Class<?> entityType) {
        return true;
    }
}
