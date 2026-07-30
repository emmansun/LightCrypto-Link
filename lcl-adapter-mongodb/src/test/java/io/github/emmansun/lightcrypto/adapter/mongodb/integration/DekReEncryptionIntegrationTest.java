package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import io.github.emmansun.lightcrypto.core.format.WireFormatDecoder;
import io.github.emmansun.lightcrypto.service.DekReEncryptionService;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.ReEncryptOptions;
import io.github.emmansun.lightcrypto.service.ReEncryptResult;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for DEK re-encryption orchestration engine.
 * Verifies the full lifecycle: save → rotate → re-encrypt → verify data integrity.
 */
@SpringBootTest(classes = IntTestApplication.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = MongoCiInitializer.class)
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DekReEncryptionIntegrationTest {

    private static final String USER_PHONE_NS = "default.default.IntTestUser#phone";

    @Autowired
    private IntTestUserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private KeyVaultService keyVaultService;

    @Autowired
    private DekReEncryptionService dekReEncryptionService;

    private final List<String> savedUserIds = new ArrayList<>();

    @BeforeAll
    void setup() {
        // Clean collections
        mongoTemplate.getDb().getCollection("__lcl_keyvault").drop();
        mongoTemplate.getDb().getCollection("__lcl_checkpoints").drop();
        mongoTemplate.dropCollection(IntTestUser.class);
    }

    @Test
    @Order(1)
    void saveEntitiesAndVerifyEncryption() {
        // Save 5 users with encrypted phone (blind index enabled)
        for (int i = 0; i < 5; i++) {
            IntTestUser user = new IntTestUser();
            user.setName("ReEncryptUser" + i);
            user.setPhone("1380000100" + i);
            userRepository.save(user);
            savedUserIds.add(user.getId());
        }

        // Verify data readable
        IntTestUser loaded = userRepository.findById(savedUserIds.get(0)).orElseThrow();
        assertThat(loaded.getPhone()).isEqualTo("13800001000");

        // Verify blind index query works
        IntTestUser found = userRepository.findByPhone("13800001003");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("ReEncryptUser3");

        // Verify raw document is encrypted (dekVersion = 1)
        Document raw = getRawUserDoc(savedUserIds.get(0));
        int dekVersion = extractDekVersion(raw, "phone");
        assertThat(dekVersion).isEqualTo(1);
    }

    @Test
    @Order(2)
    void rotateDekAndVerifyOldDataStillReadable() {
        // Rotate the DEK — new version becomes 2
        keyVaultService.rotateDek(USER_PHONE_NS);
        assertThat(keyVaultService.getActiveDekVersion(USER_PHONE_NS)).isEqualTo(2);

        // Old data should still be readable (decrypted with old DEK v1)
        IntTestUser loaded = userRepository.findById(savedUserIds.get(1)).orElseThrow();
        assertThat(loaded.getPhone()).isEqualTo("13800001001");

        // Blind index query still works (old HMAC key still in vault)
        IntTestUser found = userRepository.findByPhone("13800001004");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("ReEncryptUser4");
    }

    @Test
    @Order(3)
    void reEncryptMigratesAllDocuments() {
        ReEncryptOptions options = ReEncryptOptions.forEntity(IntTestUser.class)
                .withBatchSize(10)
                .withTaskId("int-test-reencrypt");

        ReEncryptResult result = dekReEncryptionService.reEncrypt(IntTestUser.class, options);

        // All 5 documents should be processed (they all have dekVersion=1, active=2)
        assertThat(result.success()).isTrue();
        assertThat(result.docsProcessed()).isEqualTo(5);
        assertThat(result.docsFailed()).isEqualTo(0);
        assertThat(result.fieldsReEncrypted()).isEqualTo(5); // one encrypted field per doc
    }

    @Test
    @Order(4)
    void verifyDataIntegrityAfterReEncryption() {
        // All users should still be readable with correct data
        for (int i = 0; i < 5; i++) {
            IntTestUser loaded = userRepository.findById(savedUserIds.get(i)).orElseThrow();
            assertThat(loaded.getPhone()).isEqualTo("1380000100" + i);
            assertThat(loaded.getName()).isEqualTo("ReEncryptUser" + i);
        }
    }

    @Test
    @Order(5)
    void verifyBlindIndexQueryAfterReEncryption() {
        // Blind index should be recomputed with new HMAC key — queries must still work
        IntTestUser found = userRepository.findByPhone("13800001002");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("ReEncryptUser2");
        assertThat(found.getPhone()).isEqualTo("13800001002");

        // Batch query
        List<IntTestUser> batch = userRepository.findByPhoneIn(
                List.of("13800001000", "13800001004"));
        assertThat(batch).hasSize(2);
    }

    @Test
    @Order(6)
    void verifyRawDocumentHasNewDekVersion() {
        // After re-encryption, raw docs should have dekVersion=2
        for (String id : savedUserIds) {
            Document raw = getRawUserDoc(id);
            int dekVersion = extractDekVersion(raw, "phone");
            assertThat(dekVersion)
                    .as("Document %s should have dekVersion=2", id)
                    .isEqualTo(2);
        }
    }

    @Test
    @Order(7)
    void reEncryptIsIdempotent() {
        // Running re-encryption again should skip all docs (already at active version)
        ReEncryptOptions options = ReEncryptOptions.forEntity(IntTestUser.class)
                .withBatchSize(10)
                .withTaskId("int-test-reencrypt-idempotent");

        ReEncryptResult result = dekReEncryptionService.reEncrypt(IntTestUser.class, options);

        assertThat(result.success()).isTrue();
        assertThat(result.docsProcessed()).isEqualTo(0);
        assertThat(result.docsSkipped()).isEqualTo(5);
        assertThat(result.fieldsReEncrypted()).isEqualTo(0);
    }

    @Test
    @Order(8)
    void dryRunDoesNotModifyDocuments() {
        // Rotate again to create v3
        keyVaultService.rotateDek(USER_PHONE_NS);
        assertThat(keyVaultService.getActiveDekVersion(USER_PHONE_NS)).isEqualTo(3);

        // Dry run should count but not modify
        ReEncryptOptions options = ReEncryptOptions.forEntity(IntTestUser.class)
                .withBatchSize(10)
                .withTaskId("int-test-dryrun")
                .withDryRun(true);

        ReEncryptResult result = dekReEncryptionService.reEncrypt(IntTestUser.class, options);

        assertThat(result.success()).isTrue();
        assertThat(result.fieldsReEncrypted()).isEqualTo(5); // detected 5 fields needing re-encryption

        // Raw docs should still be at dekVersion=2 (not modified)
        Document raw = getRawUserDoc(savedUserIds.get(0));
        int dekVersion = extractDekVersion(raw, "phone");
        assertThat(dekVersion).isEqualTo(2);
    }

    @Test
    @Order(9)
    void checkpointIsPersisted() {
        // Verify checkpoint was saved
        Document checkpoint = mongoTemplate.getDb().getCollection("__lcl_checkpoints")
                .find(new Document("_id", "int-test-reencrypt")).first();
        assertThat(checkpoint).isNotNull();
        assertThat(checkpoint.getString("cursorState")).isNotNull();
    }

    // ===== Helper methods =====

    private Document getRawUserDoc(String id) {
        return mongoTemplate.getDb().getCollection("intTestUser")
                .find(new Document("_id", new org.bson.types.ObjectId(id))).first();
    }

    private int extractDekVersion(Document rawDoc, String fieldName) {
        Object field = rawDoc.get(fieldName);
        assertThat(field).isInstanceOf(Document.class);
        Document encryptedPayload = (Document) field;
        String blob = encryptedPayload.getString("c");
        assertThat(blob).isNotNull();
        WireFormatDecoder.DecodedBlob decoded = WireFormatDecoder.decodeFromBase64Url(blob);
        return decoded.dekVersion();
    }
}
