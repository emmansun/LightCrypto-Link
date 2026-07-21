package io.github.emmansun.lightcrypto.adapter.mongodb;

import com.mongodb.client.result.UpdateResult;
import io.github.emmansun.lightcrypto.exception.OptimisticLockException;
import io.github.emmansun.lightcrypto.spi.VaultDocument;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyEntry;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyStatus;
import io.github.emmansun.lightcrypto.spi.VaultStore;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB implementation of {@link VaultStore} using {@link MongoTemplate}.
 *
 * <p>Vault documents are stored in the {@code __lcl_keyvault} collection with
 * {@code _id} = {@code lcl-dek-{namespace}}.
 *
 * <p>This implementation is thread-safe as it delegates to MongoTemplate which
 * handles connection pooling and thread safety internally.
 *
 * @since 1.0.0
 */
public class MongoVaultStore implements VaultStore {

    private static final String COLLECTION_NAME = "__lcl_keyvault";
    private static final String VAULT_ID_PREFIX = "lcl-dek-";

    private final MongoTemplate mongoTemplate;

    public MongoVaultStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(VaultDocument doc) {
        Document bsonDoc = toBsonDocument(doc);
        mongoTemplate.save(bsonDoc, COLLECTION_NAME);
    }

    @Override
    public Optional<VaultDocument> load(String namespace) {
        String vaultId = VAULT_ID_PREFIX + namespace;
        Query query = new Query(Criteria.where("_id").is(vaultId));
        Document bsonDoc = mongoTemplate.findOne(query, Document.class, COLLECTION_NAME);
        if (bsonDoc == null) {
            return Optional.empty();
        }
        return Optional.of(fromBsonDocument(bsonDoc));
    }

    @Override
    public boolean exists(String namespace) {
        String vaultId = VAULT_ID_PREFIX + namespace;
        Query query = new Query(Criteria.where("_id").is(vaultId));
        return mongoTemplate.exists(query, COLLECTION_NAME);
    }

    @Override
    public VaultDocument rotate(VaultDocument updatedDoc) {
        String vaultId = VAULT_ID_PREFIX + updatedDoc.namespace();
        long expectedVersion = updatedDoc.version() - 1;

        Document replacement = toBsonDocument(updatedDoc);

        Document filter = new Document("_id", vaultId)
                .append("v", expectedVersion);

        UpdateResult result = mongoTemplate.getDb()
                .getCollection(COLLECTION_NAME)
                .replaceOne(filter, replacement);

        if (result.getMatchedCount() == 0) {
            throw new OptimisticLockException(
                    "Concurrent vault rotation detected for namespace: " + updatedDoc.namespace()
                            + ". Expected version " + expectedVersion + " but document was modified.");
        }

        return updatedDoc;
    }

    @Override
    public List<VaultDocument> loadAll() {
        List<Document> docs = mongoTemplate.findAll(Document.class, COLLECTION_NAME);
        List<VaultDocument> result = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            result.add(fromBsonDocument(doc));
        }
        return result;
    }

    // ===== BSON Mapping =====

    private Document toBsonDocument(VaultDocument doc) {
        Document bsonDoc = new Document();
        bsonDoc.put("_id", VAULT_ID_PREFIX + doc.namespace());
        bsonDoc.put("namespace", doc.namespace());
        bsonDoc.put("v", doc.version());
        bsonDoc.put("activeKid", doc.activeKid());
        bsonDoc.put("cmkProvider", doc.cmkProvider());
        bsonDoc.put("cmkId", doc.cmkId());

        if (doc.createdAt() != null) {
            bsonDoc.put("createdAt", java.util.Date.from(doc.createdAt()));
        }
        if (doc.updatedAt() != null) {
            bsonDoc.put("updatedAt", java.util.Date.from(doc.updatedAt()));
        }

        List<Document> keysList = new ArrayList<>();
        if (doc.keys() != null) {
            for (KeyEntry entry : doc.keys()) {
                keysList.add(toBsonKeyEntry(entry));
            }
        }
        bsonDoc.put("keys", keysList);

        return bsonDoc;
    }

    private Document toBsonKeyEntry(KeyEntry entry) {
        Document keyDoc = new Document();
        keyDoc.put("kid", entry.kid());
        keyDoc.put("status", entry.status().name());
        keyDoc.put("wrappedDek", Base64.getEncoder().encodeToString(entry.wrappedDek()));
        keyDoc.put("wrappedHmac", Base64.getEncoder().encodeToString(entry.wrappedHmac()));
        keyDoc.put("wrappingAlg", entry.wrappingAlgorithm());
        keyDoc.put("dekKcv", entry.dekKcv());
        keyDoc.put("hmacKcv", entry.hmacKcv());
        keyDoc.put("binding", entry.binding());
        if (entry.createdAt() != null) {
            keyDoc.put("createdAt", java.util.Date.from(entry.createdAt()));
        }
        return keyDoc;
    }

    @SuppressWarnings("unchecked")
    private VaultDocument fromBsonDocument(Document bsonDoc) {
        String namespace = bsonDoc.getString("namespace");
        // Fallback: extract namespace from _id if namespace field is missing
        if (namespace == null) {
            String id = bsonDoc.getString("_id");
            if (id != null && id.startsWith(VAULT_ID_PREFIX)) {
                namespace = id.substring(VAULT_ID_PREFIX.length());
            }
        }

        long version = bsonDoc.containsKey("v") ? bsonDoc.getLong("v") : 1L;
        String activeKid = bsonDoc.getString("activeKid");
        String cmkProvider = bsonDoc.getString("cmkProvider");
        String cmkId = bsonDoc.getString("cmkId");

        Instant createdAt = toInstant(bsonDoc.get("createdAt"));
        Instant updatedAt = toInstant(bsonDoc.get("updatedAt"));

        List<KeyEntry> keys = new ArrayList<>();
        Object keysObj = bsonDoc.get("keys");
        if (keysObj instanceof List<?> keysList) {
            for (Object item : keysList) {
                if (item instanceof Document keyDoc) {
                    keys.add(fromBsonKeyEntry(keyDoc));
                }
            }
        }

        return new VaultDocument(namespace, keys, activeKid, version, cmkProvider, cmkId, createdAt, updatedAt);
    }

    private KeyEntry fromBsonKeyEntry(Document keyDoc) {
        String kid = keyDoc.getString("kid");
        String statusStr = keyDoc.getString("status");
        KeyStatus status = KeyStatus.valueOf(statusStr != null ? statusStr : "ACTIVE");

        byte[] wrappedDek = decodeBase64(keyDoc.getString("wrappedDek"));
        byte[] wrappedHmac = decodeBase64(keyDoc.getString("wrappedHmac"));
        String wrappingAlgorithm = keyDoc.getString("wrappingAlg");
        if (wrappingAlgorithm == null) {
            wrappingAlgorithm = "RSA-OAEP-256"; // backward compatibility
        }

        String dekKcv = keyDoc.getString("dekKcv");
        String hmacKcv = keyDoc.getString("hmacKcv");
        String binding = keyDoc.getString("binding");
        Instant createdAt = toInstant(keyDoc.get("createdAt"));

        return new KeyEntry(kid, status, wrappedDek, wrappedHmac, wrappingAlgorithm, dekKcv, hmacKcv, binding, createdAt);
    }

    private byte[] decodeBase64(String value) {
        if (value == null) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(value);
    }

    private Instant toInstant(Object value) {
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }
}
