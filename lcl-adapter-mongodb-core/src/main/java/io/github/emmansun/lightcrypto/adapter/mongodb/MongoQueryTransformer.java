package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.spi.BlindIndexFieldChecker;
import io.github.emmansun.lightcrypto.spi.HmacKeyProvider;
import io.github.emmansun.lightcrypto.spi.QueryTransformer;
import io.github.emmansun.lightcrypto.service.TypeSerializer;

/**
 * MongoDB implementation of {@link QueryTransformer}.
 *
 * <p>Rewrites plaintext field references to blind-index query targets:
 * <ul>
 *   <li>Field name: {@code field} → {@code field.b}</li>
 *   <li>Query value: plaintext → HMAC-SHA-256 blind index hash</li>
 * </ul>
 *
 * <p>This implementation is thread-safe. It holds references to key provider
 * and field checker but does not cache query results.
 *
 * @since 1.0.0
 */
public class MongoQueryTransformer implements QueryTransformer {

    private static final String BLIND_INDEX_SUFFIX = ".b";

    private final HmacKeyProvider hmacKeyProvider;
    private final BlindIndexFieldChecker fieldChecker;
    private final TypeSerializer typeSerializer;

    public MongoQueryTransformer(HmacKeyProvider hmacKeyProvider,
                                 BlindIndexFieldChecker fieldChecker,
                                 TypeSerializer typeSerializer) {
        this.hmacKeyProvider = hmacKeyProvider;
        this.fieldChecker = fieldChecker;
        this.typeSerializer = typeSerializer;
    }

    @Override
    public String rewriteFieldName(String originalField) {
        return originalField + BLIND_INDEX_SUFFIX;
    }

    @Override
    public Object rewriteQueryValue(Object plaintextValue, String namespace) {
        byte[] hmacKey = hmacKeyProvider.getActiveHmacKey(namespace);
        BlindIndexEngine engine = new BlindIndexEngine(hmacKey);

        Namespace ns = Namespace.parse(namespace);
        String fieldName = extractFieldName(namespace);
        return BlindIndexValueEncoder.computeBlindIndex(
            engine, typeSerializer, ns, fieldName, plaintextValue);
    }

    @Override
    public boolean supportsField(String field, Class<?> entityType) {
        return fieldChecker.hasBlindIndex(field, entityType);
    }

    /**
     * Extracts the field name from a canonical namespace.
     * Namespace format: tenant.realm.Entity#field
     */
    private String extractFieldName(String namespace) {
        int hashIndex = namespace.indexOf('#');
        if (hashIndex >= 0 && hashIndex < namespace.length() - 1) {
            return namespace.substring(hashIndex + 1);
        }
        return namespace;
    }
}
