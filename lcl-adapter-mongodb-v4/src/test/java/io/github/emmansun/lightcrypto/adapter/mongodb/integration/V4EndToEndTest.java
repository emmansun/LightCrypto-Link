package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import io.github.emmansun.lightcrypto.service.KeyVaultService;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the Spring Boot 4.x adapter module.
 *
 * <p>Uses Testcontainers MongoDB when Docker is available, otherwise falls back
 * to a MongoDB instance on localhost:27017 (e.g. GitHub Actions service container).
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>The v4 auto-configuration loads correctly with Spring Boot 4.x</li>
 *   <li>Transparent encryption/decryption works via the v4 query layer</li>
 *   <li>Blind-index query rewriting works with ValueExpressionDelegate</li>
 *   <li>Data stored in MongoDB is actually encrypted on disk</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(classes = IntTestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf(value = "mongoAvailable", disabledReason = "MongoDB not available")
class V4EndToEndTest {

    private static final int MONGO_PORT = 27017;

    /**
     * Testcontainers MongoDB — started only if Docker is available.
     * The {@code @ServiceConnection} annotation auto-configures the MongoDB connection URI.
     */
    @Container
    @ServiceConnection
    static MongoDBContainer mongo;

    static {
        if (isDockerAvailable()) {
            mongo = new MongoDBContainer("mongo:6.0");
        }
    }

    /**
     * Check if MongoDB is reachable (either Testcontainers or localhost).
     */
    @SuppressWarnings("unused")
    static boolean mongoAvailable() {
        if (isDockerAvailable()) {
            return true;
        }
        // Fallback: check localhost:27017
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", MONGO_PORT), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isDockerAvailable() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"docker", "info"});
            int exit = p.waitFor();
            return exit == 0;
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

    @BeforeEach
    void cleanCollections() {
        mongoTemplate.dropCollection(IntTestUser.class);
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

        // Read raw BSON document — encrypted fields should NOT contain plaintext
        Document raw = mongoTemplate.getCollection("intTestUser")
                .find(new Document("_id", user.getId())).first();
        assertThat(raw).isNotNull();
        assertThat(raw.getString("name")).isEqualTo("Bob"); // unencrypted
        assertThat(raw.getString("phone")).isNotEqualTo("13900139001"); // encrypted
        assertThat(raw.getString("email")).isNotEqualTo("bob@example.com"); // encrypted
    }

    // ===== Blind Index Query =====

    @Test
    @Order(3)
    void blindIndexQueryRewrite() {
        IntTestUser user = new IntTestUser();
        user.setName("Charlie");
        user.setPhone("13700137001");

        userRepository.save(user);

        // Method-name query should be rewritten to blind-index lookup
        IntTestUser found = userRepository.findByPhone("13700137001");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Charlie");
        assertThat(found.getPhone()).isEqualTo("13700137001");
    }

    @Test
    @Order(4)
    void blindIndexQueryReturnsNullForNonExistent() {
        IntTestUser found = userRepository.findByPhone("99999999999");
        assertThat(found).isNull();
    }

    @Test
    @Order(5)
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
        assertThat(found).hasSize(2);
        assertThat(found).extracting(IntTestUser::getName).containsExactlyInAnyOrder("Dave", "Eve");
    }

    // ===== KeyVault Service =====

    @Test
    @Order(6)
    void vaultAutoInitializes() {
        // Trigger vault initialization via a save operation
        IntTestUser user = new IntTestUser();
        user.setName("VaultInit");
        user.setPhone("13500135001");
        userRepository.save(user);

        // KeyVaultService should be initialized and vault document should exist
        assertThat(keyVaultService).isNotNull();
        Document vaultDoc = mongoTemplate.getCollection("vault")
                .find(new Document("namespace", "default.default.IntTestUser#phone")).first();
        assertThat(vaultDoc).isNotNull();
    }

    // ===== Update and Delete =====

    @Test
    @Order(7)
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
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Frank");
    }

    @Test
    @Order(8)
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
