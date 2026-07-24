package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import io.github.emmansun.lightcrypto.adapter.mongodb.CryptoMongoQueryCreator;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the Spring Boot 4.x adapter module.
 *
 * <p>Connects to a MongoDB instance at localhost:27017 (provided by the CI
 * service container or a local MongoDB). Tests are skipped when MongoDB is
 * not reachable.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>The v4 auto-configuration loads correctly with Spring Boot 4.x</li>
 *   <li>Transparent encryption/decryption works via the v4 query layer</li>
 *   <li>Blind-index query rewriting works with ValueExpressionDelegate</li>
 *   <li>Data stored in MongoDB is actually encrypted on disk</li>
 * </ul>
 */
@SpringBootTest(classes = IntTestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf(value = "mongoAvailable", disabledReason = "MongoDB not available on localhost:27017")
class V4EndToEndTest {

    private static final int MONGO_PORT = 27017;

    /**
     * Check if MongoDB is reachable at localhost:27017.
     */
    @SuppressWarnings("unused")
    static boolean mongoAvailable() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", MONGO_PORT), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired
    private IntTestUserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private KeyVaultService keyVaultService;

    @Autowired
    private CryptoMongoQueryCreator cryptoMongoQueryCreator;

    @Autowired
    private EntityMetadataCache entityMetadataCache;

    @BeforeEach
    void cleanCollections() {
        mongoTemplate.dropCollection(IntTestUser.class);
        // Clean vault collection to ensure test isolation (shared MongoDB with SB3 adapter tests)
        mongoTemplate.getDb().getCollection("__lcl_keyvault").drop();
    }

    // ===== Encrypted CRUD =====

    @Test
    @Order(1)
    void saveAndLoadEncryptsAndDecrypts() {
        IntTestUser user = new IntTestUser();
        user.setName("Alice");
        user.setPhone("13800138001");
        user.setEmail("alice@example.com");

        userRepository.save(user);
        assertThat(user.getId()).isNotNull();

        // Load and verify decryption
        IntTestUser loaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Alice");
        assertThat(loaded.getPhone()).isEqualTo("13800138001");
        assertThat(loaded.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @Order(2)
    void storedDataIsEncrypted() {
        IntTestUser user = new IntTestUser();
        user.setName("Bob");
        user.setPhone("13900139001");
        user.setEmail("bob@example.com");

        userRepository.save(user);
        assertThat(user.getId()).isNotNull();

        // Read raw BSON document — use ObjectId since Spring Data MongoDB 5.x
        // stores @Id String fields as ObjectId by default
        Object idValue = user.getId();
        try {
            idValue = new ObjectId(user.getId());
        } catch (IllegalArgumentException ignored) {
            // ID is not a valid ObjectId hex string, use as-is
        }

        Document raw = mongoTemplate.getDb().getCollection("intTestUser")
                .find(new Document("_id", idValue)).first();

        // Fallback: find by name if ObjectId query didn't work
        if (raw == null) {
            raw = mongoTemplate.getDb().getCollection("intTestUser")
                    .find(new Document("name", "Bob")).first();
        }

        assertThat(raw).as("Raw BSON document should exist for saved user").isNotNull();
        assertThat(raw.getString("name")).isEqualTo("Bob"); // unencrypted

        // Encrypted fields are stored as sub-documents {v, c, iv, ...}, not plain strings
        Object phoneRaw = raw.get("phone");
        Object emailRaw = raw.get("email");
        assertThat(phoneRaw).as("phone should be stored as encrypted sub-document").isInstanceOf(Document.class);
        assertThat(emailRaw).as("email should be stored as encrypted sub-document").isInstanceOf(Document.class);
    }

    // ===== Blind Index Query =====

    /**
     * Diagnostic: verify that CryptoMongoQueryCreator can rewrite queries directly.
     * This isolates whether the issue is in the query creator or in the repository layer.
     */
    @Test
    @Order(3)
    void queryCreatorRewritesEncryptedFields() {
        // Ensure vault is initialized for the phone namespace
        IntTestUser user = new IntTestUser();
        user.setName("DiagTest");
        user.setPhone("13000000001");
        userRepository.save(user);

        // Build a simple query: {phone: "13700137001"}
        Query original = new BasicQuery(new Document("phone", "13700137001"));

        // Rewrite using CryptoMongoQueryCreator
        Query rewritten = cryptoMongoQueryCreator.rewrite(original, IntTestUser.class);
        Document rewrittenDoc = rewritten.getQueryObject();

        // The rewritten query should NOT contain the original "phone" field name
        // It should be replaced with a blind-index field name (e.g., "__bi_phone")
        assertThat(rewrittenDoc.containsKey("phone"))
                .as("Query should be rewritten: 'phone' field should be replaced with blind-index field")
                .isFalse();
    }

    @Test
    @Order(4)
    void blindIndexQueryRewrite() {
        IntTestUser user = new IntTestUser();
        user.setName("Charlie");
        user.setPhone("13700137001");

        userRepository.save(user);
        assertThat(user.getId()).isNotNull();

        // Method-name query should be rewritten to blind-index lookup
        IntTestUser found = userRepository.findByPhone("13700137001");
        assertThat(found).as("findByPhone should find the saved user via blind index").isNotNull();
        assertThat(found.getName()).isEqualTo("Charlie");
        assertThat(found.getPhone()).isEqualTo("13700137001");
    }

    @Test
    @Order(5)
    void blindIndexQueryReturnsNullForNonExistent() {
        IntTestUser found = userRepository.findByPhone("99999999999");
        assertThat(found).isNull();
    }

    @Test
    @Order(6)
    void blindIndexInQuery() {
        IntTestUser u1 = new IntTestUser();
        u1.setName("Dave");
        u1.setPhone("13600136001");

        IntTestUser u2 = new IntTestUser();
        u2.setName("Eve");
        u2.setPhone("13600136002");

        userRepository.save(u1);
        userRepository.save(u2);

        List<IntTestUser> found = userRepository.findByPhoneIn(List.of("13600136001", "13600136002"));
        assertThat(found).as("findByPhoneIn should find both users via blind index").hasSize(2);
        assertThat(found).extracting(IntTestUser::getName).containsExactlyInAnyOrder("Dave", "Eve");
    }

    // ===== KeyVault Service =====

    @Test
    @Order(7)
    void vaultAutoInitializes() {
        // Trigger vault initialization via a save operation
        IntTestUser user = new IntTestUser();
        user.setName("VaultInit");
        user.setPhone("13500135001");
        userRepository.save(user);

        // KeyVaultService should be initialized and vault document should exist
        assertThat(keyVaultService).isNotNull();
        // Vault documents are stored in the "__lcl_keyvault" collection with "_id" = "lcl-dek-{namespace}"
        Document vaultDoc = mongoTemplate.getDb().getCollection("__lcl_keyvault")
                .find(new Document("_id", "lcl-dek-default.default.IntTestUser#phone")).first();
        assertThat(vaultDoc).isNotNull();
    }

    // ===== Update and Delete =====

    @Test
    @Order(8)
    void updateReEncryptsField() {
        IntTestUser user = new IntTestUser();
        user.setName("Frank");
        user.setPhone("13400134001");
        userRepository.save(user);

        user.setPhone("13400134002");
        userRepository.save(user);

        IntTestUser loaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(loaded.getPhone()).isEqualTo("13400134002");

        // Verify findByPhone works with the new phone value
        IntTestUser found = userRepository.findByPhone("13400134002");
        assertThat(found).as("findByPhone should find the updated user via blind index").isNotNull();
        assertThat(found.getName()).isEqualTo("Frank");
    }

    @Test
    @Order(9)
    void deleteRemovesDocument() {
        IntTestUser user = new IntTestUser();
        user.setName("Grace");
        user.setPhone("13300133001");
        userRepository.save(user);

        userRepository.deleteById(user.getId());

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(userRepository.findByPhone("13300133001")).isNull();
    }
}
