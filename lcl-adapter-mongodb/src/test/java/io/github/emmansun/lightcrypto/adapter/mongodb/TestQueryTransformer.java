package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.spi.QueryTransformer;

import java.nio.charset.StandardCharsets;

/**
 * Simple QueryTransformer for unit tests.
 * Mirrors the behavior of MongoQueryTransformer using a fixed HMAC key.
 */
public class TestQueryTransformer implements QueryTransformer {

    private final byte[] hmacKey;

    public TestQueryTransformer(byte[] hmacKey) {
        this.hmacKey = hmacKey;
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
        byte[] valueBytes = serializeValue(plaintextValue);
        return engine.computeBlindIndex(ns, fieldName, valueBytes);
    }

    @Override
    public boolean supportsField(String field, Class<?> entityType) {
        return true;
    }

    private byte[] serializeValue(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof String str) {
            return str.getBytes(StandardCharsets.UTF_8);
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }
}
