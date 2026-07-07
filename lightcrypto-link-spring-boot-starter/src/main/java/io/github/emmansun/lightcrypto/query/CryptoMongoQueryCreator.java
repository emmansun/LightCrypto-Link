package io.github.emmansun.lightcrypto.query;

import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Encrypted query rewriter — rewrites queries on encrypted fields into blind-index queries.
 */
public class CryptoMongoQueryCreator {

    private final EntityMetadataCache metadataCache;
    private final CryptoCodec cryptoCodec;
    private final TypeSerializer typeSerializer;
    private final KeyVaultService keyVaultService;

    public CryptoMongoQueryCreator(EntityMetadataCache metadataCache,
                                   CryptoCodec cryptoCodec,
                                   TypeSerializer typeSerializer,
                                   KeyVaultService keyVaultService) {
        this.metadataCache = metadataCache;
        this.cryptoCodec = cryptoCodec;
        this.typeSerializer = typeSerializer;
        this.keyVaultService = keyVaultService;
    }

    /**
     * Rewrite encrypted field criteria in the given Query.
     */
    public Query rewrite(Query query, Class<?> entityClass) {
        if (!metadataCache.hasEncryptedFields(entityClass)) return query;

        List<EncryptedFieldMetadata> fields = metadataCache.getEncryptedFields(entityClass);
        Document criteriaDoc = query.getQueryObject();

        rewriteCriteriaDocument(criteriaDoc, fields, entityClass);

        Query rewritten = new Query();
        for (Map.Entry<String, Object> entry : criteriaDoc.entrySet()) {
            rewritten.addCriteria(new Criteria(entry.getKey()).is(entry.getValue()));
        }
        return rewritten;
    }

    private void rewriteCriteriaDocument(Document doc, List<EncryptedFieldMetadata> fields, Class<?> entityClass) {
        List<String> keys = new ArrayList<>(doc.keySet());
        for (String key : keys) {
            // Handle $and / $or
            if (key.startsWith("$")) {
                Object val = doc.get(key);
                if (val instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Document subDoc) {
                            rewriteCriteriaDocument(subDoc, fields, entityClass);
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

            // $in query: hash each element in the list
            if (value instanceof Document subDoc && subDoc.containsKey("$in")) {
                List<?> inValues = (List<?>) subDoc.get("$in");
                List<String> hashed = inValues.stream()
                        .map(v -> hashValue(matched, v, entityClass))
                        .toList();
                doc.put(matched.bsonFieldName() + ".b", new Document("$in", hashed));
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

            // Simple 'is' query
            String hashed = hashValue(matched, value, entityClass);
            doc.put(matched.bsonFieldName() + ".b", hashed);
            doc.remove(key);
        }
    }

    private String hashValue(EncryptedFieldMetadata meta, Object value, Class<?> entityClass) {
        byte[] serialized = typeSerializer.serialize(value);
        keyVaultService.ensureVaultInitialized(entityClass);
        String activeKid = keyVaultService.getActiveKid(entityClass);
        byte[] hmacKey = keyVaultService.getHmacKey(activeKid);
        return cryptoCodec.generateBlindIndex(hmacKey, meta.blindIndexFieldName(), serialized);
    }

    private EncryptedFieldMetadata findEncryptedField(String key, List<EncryptedFieldMetadata> fields) {
        return fields.stream()
            .filter(f -> f.bsonFieldName().equals(key))
                .findFirst()
                .orElse(null);
    }
}
