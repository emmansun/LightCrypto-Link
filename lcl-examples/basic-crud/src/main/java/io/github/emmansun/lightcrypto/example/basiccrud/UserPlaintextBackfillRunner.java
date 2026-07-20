package io.github.emmansun.lightcrypto.example.basiccrud;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Backfill runner for migration window: convert historical plaintext User records
 * to encrypted format by loading entities and saving them through repository path.
 *
 * Usage:
 * 1) Prepare test data: insert plaintext records into "user" collection first.
 * 2) Disable demo runner to avoid collection reset:
 *    -Dlcl.demo.enabled=false
 * 3) Dry-run first (recommended):
 *    -Dlcl.migration.backfill.enabled=true
 *    -Dlcl.migration.backfill.dry-run=true
 * 4) Run real backfill after dry-run verification:
 *    -Dlcl.migration.backfill.dry-run=false
 * 5) Optional tuning:
 *    -Dlcl.migration.backfill.batch-size=500
 *    -Dlcl.migration.backfill.start-after-id=<last-cursor>
 *    -Dlcl.migration.backfill.max-batches=0  (0 means unlimited)
 *
 * Example:
 * mvn -pl lightcrypto-link-examples/basic-crud spring-boot:run
 *   -Dspring-boot.run.jvmArguments="-Dlcl.demo.enabled=false -Dlcl.migration.backfill.enabled=true
 *   -Dlcl.migration.backfill.dry-run=true -Dlcl.migration.backfill.batch-size=500"
 */
@Component
@ConditionalOnProperty(name = "lcl.migration.backfill.enabled", havingValue = "true")
public class UserPlaintextBackfillRunner implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;

    public UserPlaintextBackfillRunner(MongoTemplate mongoTemplate, UserRepository userRepository) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        int batchSize = getIntProp("lcl.migration.backfill.batch-size", 500);
        boolean dryRun = getBoolProp("lcl.migration.backfill.dry-run", true);
        String cursor = getStringProp("lcl.migration.backfill.start-after-id", "");
        int maxBatches = getIntProp("lcl.migration.backfill.max-batches", 0);

        int processed = 0;
        int saved = 0;
        int batches = 0;

        System.out.println("[BACKFILL] Start User plaintext backfill");
        System.out.println("[BACKFILL] dryRun=" + dryRun + ", batchSize=" + batchSize
            + ", startAfterId=" + (cursor.isBlank() ? "<begin>" : cursor)
            + ", maxBatches=" + (maxBatches <= 0 ? "unlimited" : maxBatches));

        while (true) {
            if (maxBatches > 0 && batches >= maxBatches) {
                System.out.println("[BACKFILL] Reached max-batches limit: " + maxBatches);
                break;
            }

            List<Document> candidates = fetchCandidateDocs(cursor, batchSize);
            if (candidates.isEmpty()) {
                break;
            }

            batches++;
            List<String> ids = new ArrayList<>(candidates.size());
            for (Document doc : candidates) {
                Object rawId = doc.get("_id");
                if (rawId != null) {
                    ids.add(toIdString(rawId));
                }
            }

            for (String id : ids) {
                processed++;
                User user = userRepository.findById(id).orElse(null);
                if (user == null) {
                    continue;
                }
                if (!dryRun) {
                    userRepository.save(user);
                    saved++;
                }
                cursor = id;
            }

            System.out.println("[BACKFILL] batch=" + batches
                + ", candidates=" + candidates.size()
                + ", processed=" + processed
                + ", saved=" + saved
                + ", cursor=" + cursor);
        }

        System.out.println("[BACKFILL] Finished. processed=" + processed + ", saved=" + saved
            + ", dryRun=" + dryRun + ", lastCursor=" + (cursor.isBlank() ? "<none>" : cursor));
    }

    private List<Document> fetchCandidateDocs(String startAfterId, int batchSize) {
        Query query = new Query();
        query.addCriteria(needsBackfillCriteria());

        if (startAfterId != null && !startAfterId.isBlank()) {
            query.addCriteria(Criteria.where("_id").gt(toCursorValue(startAfterId)));
        }

        query.limit(batchSize);
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "_id"));
        query.fields().include("_id");

        return mongoTemplate.find(query, Document.class, "user");
    }

    private Criteria needsBackfillCriteria() {
        // Candidate definition for migration window:
        // - phone is plaintext or missing blind index field
        // - or age is plaintext
        // - or birthDate is plaintext
        return new Criteria().orOperator(
            Criteria.where("phone._e").exists(false),
            Criteria.where("phone.b").exists(false),
            Criteria.where("age._e").exists(false),
            Criteria.where("birthDate._e").exists(false)
        );
    }

    private int getIntProp(String key, int defaultValue) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean getBoolProp(String key, boolean defaultValue) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(v.trim());
    }

    private String getStringProp(String key, String defaultValue) {
        String v = System.getProperty(key);
        return (v == null) ? defaultValue : v.trim();
    }

    private Object toCursorValue(String id) {
        if (ObjectId.isValid(id)) {
            return new ObjectId(id);
        }
        return id;
    }

    private String toIdString(Object rawId) {
        if (rawId instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        return String.valueOf(rawId);
    }
}
