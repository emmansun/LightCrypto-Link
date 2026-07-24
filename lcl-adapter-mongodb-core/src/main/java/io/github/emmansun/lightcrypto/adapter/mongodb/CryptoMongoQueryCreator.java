package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.spi.QueryTransformer;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Encrypted query rewriter — rewrites queries on encrypted fields into blind-index queries.
 * Delegates field name and value transformation to {@link QueryTransformer}.
 */
public class CryptoMongoQueryCreator {

    private final EntityMetadataCache metadataCache;
    private final TypeSerializer typeSerializer;
    private final KeyVaultService keyVaultService;
    private final QueryTransformer queryTransformer;

    public CryptoMongoQueryCreator(EntityMetadataCache metadataCache,
                                   TypeSerializer typeSerializer,
                                   KeyVaultService keyVaultService,
                                   QueryTransformer queryTransformer) {
        this.metadataCache = metadataCache;
        this.typeSerializer = typeSerializer;
        this.keyVaultService = keyVaultService;
        this.queryTransformer = queryTransformer;
    }

    /**
     * Rewrite encrypted field criteria in the given Query.
     */
    public Query rewrite(Query query, Class<?> entityClass) {
        if (!metadataCache.hasEncryptedFields(entityClass)) return query;

        List<EncryptedFieldMetadata> fields = metadataCache.getEncryptedFields(entityClass);
        Document criteriaDoc = copyDocument(query.getQueryObject());

        rewriteCriteriaDocument(criteriaDoc, fields);

        // Rebuild query using rewritten criteria while preserving common options.
        Query rewritten = new BasicQuery(criteriaDoc, query.getFieldsObject());
        Sort sort = toSort(query.getSortObject());
        if (sort.isSorted()) {
            rewritten.with(sort);
        }
        rewritten.skip(query.getSkip());
        rewritten.limit(query.getLimit());
        query.getCollation().ifPresent(rewritten::collation);
        return rewritten;
    }

    private Sort toSort(Document sortDoc) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Map.Entry<String, Object> entry : sortDoc.entrySet()) {
            String key = entry.getKey();
            Object raw = entry.getValue();
            int direction = raw instanceof Number n ? n.intValue() : 1;
            orders.add(direction < 0 ? Sort.Order.desc(key) : Sort.Order.asc(key));
        }
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }

    private Document copyDocument(Document source) {
        Document copy = new Document();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    private Object copyValue(Object value) {
        if (value instanceof Document doc) {
            return copyDocument(doc);
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>(list.size());
            for (Object item : list) {
                copied.add(copyValue(item));
            }
            return copied;
        }
        return value;
    }

    private void rewriteCriteriaDocument(Document doc, List<EncryptedFieldMetadata> fields) {
        List<String> keys = new ArrayList<>(doc.keySet());
        for (String key : keys) {
            // Handle $and / $or
            if (key.startsWith("$")) {
                Object val = doc.get(key);
                if (val instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Document subDoc) {
                            rewriteCriteriaDocument(subDoc, fields);
                        }
                    }
                }
                continue;
            }
            // Find matching encrypted field
            EncryptedFieldMetadata matched = findEncryptedField(key, fields);
            if (matched == null) continue;

            if (!matched.blindIndex()) {
                throw new UnsupportedOperationException(
                        "Cannot query on @Encrypted field '" + key +
                                "' with blindIndex=false. Enable blindIndex to support queries.");
            }

            Object value = doc.get(key);

            // null values do not need hashing
            if (value == null) continue;

            String namespace = matched.namespace().canonical();
            String rewrittenFieldName = queryTransformer.rewriteFieldName(matched.bsonFieldName());

            // $in query: hash each element in the list
            if (value instanceof Document subDoc && subDoc.containsKey("$in")) {
                List<?> inValues = (List<?>) subDoc.get("$in");
                List<Object> hashed = inValues.stream()
                        .map(v -> hashValue(matched, v))
                        .toList();
                doc.put(rewrittenFieldName, new Document("$in", hashed));
                doc.remove(key);
                continue;
            }

            // Reject unsupported query operators
            if (value instanceof Document subDoc) {
                for (String op : subDoc.keySet()) {
                    if (op.startsWith("$") && !op.equals("$in")) {
                        throw new UnsupportedOperationException(
                                "Unsupported query operation '" + op + "' on encrypted field '" + key + "'");
                    }
                }
            }

            // Reject non-simple value types (e.g. Pattern/regex queries)
            if (!(value instanceof String) && !(value instanceof Number)
                    && !(value instanceof Boolean) && !(value instanceof Document)) {
                throw new UnsupportedOperationException(
                        "Unsupported query type '" + value.getClass().getSimpleName()
                                + "' on encrypted field '" + key + "'");
            }

            // Simple 'is' query - use QueryTransformer for value rewrite
            Object hashed = queryTransformer.rewriteQueryValue(value, namespace);
            doc.put(rewrittenFieldName, hashed);
            doc.remove(key);
        }
    }

    private Object hashValue(EncryptedFieldMetadata meta, Object value) {
        byte[] serialized = typeSerializer.serialize(value);
        String namespace = meta.namespace().canonical();
        keyVaultService.ensureVaultInitialized(namespace);
        byte[] hmacKey = keyVaultService.getActiveHmacKey(namespace);
        io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine engine =
                new io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine(hmacKey);
        return engine.computeBlindIndex(meta.namespace(), meta.blindIndexFieldName(), serialized);
    }

    private EncryptedFieldMetadata findEncryptedField(String key, List<EncryptedFieldMetadata> fields) {
        return fields.stream()
            .filter(f -> f.bsonFieldName().equals(key))
                .findFirst()
                .orElse(null);
    }
}
