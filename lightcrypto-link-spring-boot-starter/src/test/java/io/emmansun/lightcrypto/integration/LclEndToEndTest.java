package io.emmansun.lightcrypto.integration;

import io.emmansun.lightcrypto.service.KeyVaultService;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration tests with embedded MongoDB.
 * Covers tasks 6.6-6.10 (KeyVaultService) and 11.1-11.9 (E2E).
 */
@SpringBootTest(classes = IntTestApplication.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = MongoCiInitializer.class)
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LclEndToEndTest {

    @Autowired
    private IntTestUserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private KeyVaultService keyVaultService;

    @BeforeEach
    void cleanCollections() {
        mongoTemplate.dropCollection(IntTestUser.class);
        mongoTemplate.dropCollection(IntTestEmployee.class);
    }

    // ===== 6.6-6.10: KeyVaultService Tests =====

    @Test
    @Order(1)
    void vaultAutoInitCreatesDocument() {
        // Trigger lazy vault initialization by saving an entity
        IntTestUser user = new IntTestUser();
        user.setName("VaultTest");
        user.setPhone("10000000001");
        userRepository.save(user);

        // Verify vault document was created with multi-DEK structure
        Query query = new Query(Criteria.where("_id").is("lcl-dek-IntTestUser"));
        Document vaultDoc = mongoTemplate.getDb().getCollection("__lcl_keyvault")
                .find(query.getQueryObject()).first();
        assertThat(vaultDoc).isNotNull();
        assertThat(vaultDoc.getString("activeKid")).isNotNull();
        assertThat(vaultDoc.getList("keys", Document.class)).hasSize(1);
        Document keyEntry = vaultDoc.getList("keys", Document.class).get(0);
        assertThat(keyEntry.get("dek")).isNotNull();
        assertThat(keyEntry.get("hmk")).isNotNull();
        assertThat(keyEntry.get("binding")).isNotNull();
        assertThat(keyEntry.getString("kid")).startsWith("v1-");
        assertThat(keyEntry.getString("status")).isEqualTo("ACTIVE");
        assertThat(vaultDoc.get("cmk")).isNotNull();
        assertThat(vaultDoc.getInteger("v")).isEqualTo(1);
    }

    @Test
    @Order(2)
    void vaultKeysAreLoaded() {
        // Ensure vault is initialized for IntTestUser
        keyVaultService.ensureVaultInitialized(IntTestUser.class);
        String activeKid = keyVaultService.getActiveKid(IntTestUser.class);
        assertThat(activeKid).isNotNull();
        assertThat(keyVaultService.getDek(activeKid)).hasSize(32);
        assertThat(keyVaultService.getHmacKey(activeKid)).hasSize(32);
    }

    // ===== 11.1: Save and read back String with blindIndex =====

    @Test
    @Order(10)
    void saveAndReadBackStringWithBlindIndex() {
        IntTestUser user = new IntTestUser();
        user.setName("Alice");
        user.setPhone("13800138000");
        user.setEmail("alice@test.com");

        userRepository.save(user);

        // Read back
        IntTestUser loaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(loaded.getPhone()).isEqualTo("13800138000");
        assertThat(loaded.getName()).isEqualTo("Alice");
        assertThat(loaded.getEmail()).isEqualTo("alice@test.com");
    }

    // ===== 11.2: Save and read back multiple encrypted fields =====

    @Test
    @Order(11)
    void saveAndReadBackMultipleEncryptedFields() {
        IntTestEmployee emp = new IntTestEmployee();
        emp.setName("Bob");
        emp.setAge(35);
        emp.setBirthDate(LocalDate.of(1990, 6, 15));

        mongoTemplate.save(emp);

        IntTestEmployee loaded = mongoTemplate.findById(emp.getId(), IntTestEmployee.class);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("Bob");
        assertThat(loaded.getAge()).isEqualTo(35);
        assertThat(loaded.getBirthDate()).isEqualTo(LocalDate.of(1990, 6, 15));
    }

    // ===== 11.3: Blind index query — findByPhone =====

    @Test
    @Order(12)
    void blindIndexFindByPhone() {
        // Create 10 test records
        for (int i = 0; i < 10; i++) {
            IntTestUser user = new IntTestUser();
            user.setName("User" + i);
            user.setPhone("1380000000" + i);
            userRepository.save(user);
        }

        // Find specific user by phone
        IntTestUser found = userRepository.findByPhone("13800000005");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("User5");
        assertThat(found.getPhone()).isEqualTo("13800000005");
    }

    // ===== 11.4: findByPhoneIn batch query =====

    @Test
    @Order(13)
    void findByPhoneInBatch() {
        for (int i = 0; i < 5; i++) {
            IntTestUser user = new IntTestUser();
            user.setName("User" + i);
            user.setPhone("1390000000" + i);
            userRepository.save(user);
        }

        List<IntTestUser> found = userRepository.findByPhoneIn(
                List.of("13900000001", "13900000003"));
        assertThat(found).hasSize(2);
        assertThat(found.stream().map(IntTestUser::getName).toList())
                .containsExactlyInAnyOrder("User1", "User3");
    }

    // ===== 11.5: findByPhoneAndName compound query =====

    @Test
    @Order(14)
    void findByPhoneAndName() {
        IntTestUser user = new IntTestUser();
        user.setName("Charlie");
        user.setPhone("13700001111");
        userRepository.save(user);

        IntTestUser found = userRepository.findByPhoneAndName("13700001111", "Charlie");
        assertThat(found).isNotNull();
        assertThat(found.getPhone()).isEqualTo("13700001111");
    }

    // ===== 11.8: Update scenario =====

    @Test
    @Order(15)
    void updateEncryptedField() {
        IntTestUser user = new IntTestUser();
        user.setName("Dave");
        user.setPhone("13600002222");
        userRepository.save(user);

        // Update phone
        user.setPhone("13600003333");
        userRepository.save(user);

        // Read back and verify new value
        IntTestUser loaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(loaded.getPhone()).isEqualTo("13600003333");

        // Old phone should not be findable
        IntTestUser notFound = userRepository.findByPhone("13600002222");
        assertThat(notFound).isNull();
    }

    // ===== 11.9: Null field handling =====

    @Test
    @Order(16)
    void nullEncryptedFieldPreserved() {
        IntTestUser user = new IntTestUser();
        user.setName("Eve");
        user.setPhone(null); // null encrypted field
        userRepository.save(user);

        IntTestUser loaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(loaded.getPhone()).isNull();
        assertThat(loaded.getName()).isEqualTo("Eve");
    }
}
