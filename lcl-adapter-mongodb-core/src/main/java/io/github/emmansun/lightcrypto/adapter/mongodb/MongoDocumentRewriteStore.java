package io.github.emmansun.lightcrypto.adapter.mongodb;

import com.mongodb.CursorType;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import io.github.emmansun.lightcrypto.spi.DocumentRewriteStore;
import io.github.emmansun.lightcrypto.spi.RawDocument;
import io.github.emmansun.lightcrypto.spi.ScanOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB implementation of {@link DocumentRewriteStore} using {@link MongoTemplate}.
 * <p>
 * Provides cursor-based batch scanning with stable {@code _id} order, per-field
 * kid-based CAS replacement via dot-notation {@code _k} filters, and checkpoint
 * persistence in a dedicated collection.
 *
 * @since 1.1.0
 */
public class MongoDocumentRewriteStore implements DocumentRewriteStore {

    private static final String DEFAULT_CHECKPOINT_COLLECTION = "__lcl_checkpoints";

    /** Internal field keys used for routing/type preservation — not part of business data. */
    private static final String INTERNAL_COLLECTION_KEY = "_lcl_collection";
    private static final String INTERNAL_RAW_ID_KEY = "_lcl_rawId";

    private final MongoTemplate mongoTemplate;
    private final String checkpointCollection;

    public MongoDocumentRewriteStore(MongoTemplate mongoTemplate) {
        this(mongoTemplate, DEFAULT_CHECKPOINT_COLLECTION);
    }

    public MongoDocumentRewriteStore(MongoTemplate mongoTemplate,
                                     String checkpointCollection) {
        this.mongoTemplate = mongoTemplate;
        this.checkpointCollection = checkpointCollection != null ? checkpointCollection : DEFAULT_CHECKPOINT_COLLECTION;
    }

    @Override
    public CloseableIterator<RawDocument> scan(ScanOptions options) {
        String collectionName = options.collectionHint();
        int batchSize = options.batchSize() > 0 ? options.batchSize() : ScanOptions.DEFAULT_BATCH_SIZE;

        Query query = new Query();
        query.with(org.springframework.data.domain.Sort.by("_id").ascending());

        // Resume after checkpoint
        if (options.resumeAfter() != null && !options.resumeAfter().isBlank()) {
            query.addCriteria(Criteria.where("_id").gt(options.resumeAfter()));
        }

        FindIterable<Document> iterable = mongoTemplate.getCollection(collectionName)
                .find(query.getQueryObject())
                .sort(new Document("_id", 1))
                .batchSize(batchSize)
                .cursorType(CursorType.NonTailable)
                .noCursorTimeout(true);

        if (options.maxScanTime() != null) {
            iterable.maxTime(options.maxScanTime().toMillis(), TimeUnit.MILLISECONDS);
        }

        MongoCursor<Document> cursor = iterable.iterator();
        return new MongoCloseableIterator(cursor, collectionName);
    }

    @Override
    public boolean replace(RawDocument document) {
        String collectionName = extractCollectionName(document);
        Object rawId = extractRawId(document);
        Document filter = buildCasFilter(rawId, document);

        Document replacement = toBsonDocument(document);

        UpdateResult result = mongoTemplate.getCollection(collectionName)
                .replaceOne(filter, replacement);

        return result.getMatchedCount() > 0;
    }

    @Override
    public int replaceBatch(List<RawDocument> documents) {
        if (documents.isEmpty()) {
            return 0;
        }

        // Group by collection name
        Map<String, List<RawDocument>> byCollection = new LinkedHashMap<>();
        for (RawDocument doc : documents) {
            String collectionName = extractCollectionName(doc);
            byCollection.computeIfAbsent(collectionName, k -> new ArrayList<>()).add(doc);
        }

        int totalReplaced = 0;
        for (Map.Entry<String, List<RawDocument>> entry : byCollection.entrySet()) {
            String collectionName = entry.getKey();
            List<RawDocument> docs = entry.getValue();

            List<WriteModel<Document>> writes = new ArrayList<>(docs.size());
            for (RawDocument doc : docs) {
                Object rawId = extractRawId(doc);
                Document filter = buildCasFilter(rawId, doc);
                Document replacement = toBsonDocument(doc);
                writes.add(new ReplaceOneModel<>(filter, replacement, new ReplaceOptions().upsert(false)));
            }

            BulkWriteResult result = mongoTemplate.getCollection(collectionName)
                    .bulkWrite(writes, new BulkWriteOptions().ordered(false));
            totalReplaced += result.getMatchedCount();
        }

        return totalReplaced;
    }

    @Override
    public void saveCheckpoint(String taskId, String cursorState) {
        Document checkpointDoc = new Document()
                .append("_id", taskId)
                .append("cursorState", cursorState)
                .append("updatedAt", new Date());

        mongoTemplate.getCollection(checkpointCollection)
                .replaceOne(
                        new Document("_id", taskId),
                        checkpointDoc,
                        new ReplaceOptions().upsert(true));
    }

    @Override
    public Optional<String> loadCheckpoint(String taskId) {
        Document doc = mongoTemplate.getCollection(checkpointCollection)
                .find(new Document("_id", taskId))
                .first();

        if (doc == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(doc.getString("cursorState"));
    }

    // ===== Internal methods =====

    /**
     * Builds a CAS filter using per-field kid dot-notation conditions.
     * If {@code fieldKids} is empty, returns an {@code _id}-only filter.
     */
    Document buildCasFilter(Object rawId, RawDocument document) {
        Document filter = new Document("_id", rawId);
        Map<String, String> fieldKids = document.fieldKids();
        if (fieldKids != null) {
            for (Map.Entry<String, String> entry : fieldKids.entrySet()) {
                filter.append(entry.getKey() + "._k", entry.getValue());
            }
        }
        return filter;
    }

    private String extractCollectionName(RawDocument doc) {
        Object collectionHint = doc.fields().get(INTERNAL_COLLECTION_KEY);
        if (collectionHint instanceof String name) {
            return name;
        }
        throw new IllegalStateException("RawDocument missing internal collection hint: " + doc.id());
    }

    private Object extractRawId(RawDocument doc) {
        Object rawId = doc.fields().get(INTERNAL_RAW_ID_KEY);
        return rawId != null ? rawId : doc.id();
    }

    /**
     * Rebuilds the BSON document preserving all original fields (including _id, _class).
     * Only internal LCL routing keys are excluded.
     */
    private Document toBsonDocument(RawDocument doc) {
        Document bsonDoc = new Document();
        for (Map.Entry<String, Object> entry : doc.fields().entrySet()) {
            String key = entry.getKey();
            if (INTERNAL_COLLECTION_KEY.equals(key) || INTERNAL_RAW_ID_KEY.equals(key)) {
                continue;
            }
            bsonDoc.put(key, entry.getValue());
        }
        return bsonDoc;
    }

    /**
     * Closeable iterator wrapping a MongoDB cursor.
     */
    private class MongoCloseableIterator implements CloseableIterator<RawDocument> {
        private final MongoCursor<Document> cursor;
        private final String collectionName;

        MongoCloseableIterator(MongoCursor<Document> cursor, String collectionName) {
            this.cursor = cursor;
            this.collectionName = collectionName;
        }

        @Override
        public boolean hasNext() {
            return cursor.hasNext();
        }

        @Override
        public RawDocument next() {
            Document doc = cursor.next();
            return toRawDocument(doc, collectionName);
        }

        @Override
        public void close() {
            cursor.close();
        }

        private RawDocument toRawDocument(Document doc, String collectionName) {
            Object rawId = doc.get("_id");
            String id = rawId instanceof org.bson.types.ObjectId oid
                    ? oid.toHexString()
                    : String.valueOf(rawId);

            Map<String, Object> fields = new LinkedHashMap<>(doc);
            // Store internal routing metadata
            fields.put(INTERNAL_COLLECTION_KEY, collectionName);
            fields.put(INTERNAL_RAW_ID_KEY, rawId);

            // Extract per-field kid snapshots from encrypted sub-documents
            Map<String, String> fieldKids = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("_")) {
                    continue;
                }
                if (entry.getValue() instanceof Document subDoc && subDoc.containsKey("_k")) {
                    fieldKids.put(key, subDoc.getString("_k"));
                }
            }

            return new RawDocument(id, fields, fieldKids);
        }
    }
}
