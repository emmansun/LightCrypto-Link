package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration tests with embedded MongoDB.
 * Covers KeyVaultService and transparent encryption E2E scenarios.
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

    @Autowired
    private IntTestArticleRepository articleRepository;

    @Autowired
    private IntTestUserWithWholeAddressRepository wholeAddressRepository;

    @Autowired
    private IntTestUserWithWholeAddressesRepository wholeAddressesRepository;

    @Autowired
    private IntTestUserWithAddressesRepository addressesRepository;

    @Autowired
    private IntTestWholeSimpleCollectionsRepository wholeSimpleCollectionsRepository;

    @BeforeEach
    void cleanCollections() {
        mongoTemplate.dropCollection(IntTestUser.class);
        mongoTemplate.dropCollection(IntTestEmployee.class);
        mongoTemplate.dropCollection(IntTestArticle.class);
        mongoTemplate.dropCollection(IntTestWholeSimpleCollections.class);
        mongoTemplate.dropCollection(IntTestUserWithAddresses.class);
        mongoTemplate.dropCollection(IntTestUserWithWholeAddress.class);
        mongoTemplate.dropCollection(IntTestUserWithWholeAddresses.class);
        // Clean vault collection to ensure test isolation (shared MongoDB with v4 adapter tests)
        mongoTemplate.getDb().getCollection("__lcl_keyvault").drop();
    }

    // ===== KeyVaultService Tests =====

    private static final String USER_PHONE_NS = "default.default.IntTestUser#phone";
    private static final String EMPLOYEE_NS = "default.default.IntTestEmployee#age";

    @Test
    @Order(1)
    void vaultAutoInitCreatesDocument() {
        // Trigger lazy vault initialization by saving an entity
        IntTestUser user = new IntTestUser();
        user.setName("VaultTest");
        user.setPhone("10000000001");
        userRepository.save(user);

        // Verify vault document was created with multi-DEK structure (per-namespace)
        Query query = new Query(Criteria.where("_id").is("lcl-dek-" + USER_PHONE_NS));
        Document vaultDoc = mongoTemplate.getDb().getCollection("__lcl_keyvault")
                .find(query.getQueryObject()).first();
        assertThat(vaultDoc).isNotNull();
        assertThat(vaultDoc.getString("activeKid")).isNotNull();
        assertThat(vaultDoc.getList("keys", Document.class)).hasSize(1);
        Document keyEntry = vaultDoc.getList("keys", Document.class).get(0);
        assertThat(keyEntry.get("wrappedDek")).isNotNull();
        assertThat(keyEntry.get("wrappedHmac")).isNotNull();
        assertThat(keyEntry.get("binding")).isNotNull();
        assertThat(keyEntry.getString("kid")).startsWith("v1-");
        assertThat(keyEntry.getString("status")).isEqualTo("ACTIVE");
        Number versionNum = vaultDoc.get("v", Number.class);
        assertThat(versionNum.intValue()).isEqualTo(1);
    }

    @Test
    @Order(2)
    void vaultKeysAreLoaded() {
        // Ensure vault is initialized for IntTestUser phone namespace
        keyVaultService.ensureVaultInitialized(USER_PHONE_NS);
        String activeKid = keyVaultService.getActiveKid(USER_PHONE_NS);
        assertThat(activeKid).isNotNull();
        assertThat(keyVaultService.getDek(activeKid)).hasSize(32);
        assertThat(keyVaultService.getHmacKey(activeKid)).hasSize(32);
    }

    @Test
    @Order(3)
    void rotateDekAddsNewActiveVersionAndMarksOldAsRotated() {
        keyVaultService.ensureVaultInitialized(USER_PHONE_NS);
        String oldKid = keyVaultService.getActiveKid(USER_PHONE_NS);
        byte[] oldDek = keyVaultService.getDek(oldKid);
        byte[] oldHmac = keyVaultService.getHmacKey(oldKid);

        keyVaultService.rotateDek(USER_PHONE_NS);

        String newKid = keyVaultService.getActiveKid(USER_PHONE_NS);
        assertThat(newKid).isNotEqualTo(oldKid);
        assertThat(Pattern.matches("^v2-[0-9a-f]{8}$", newKid)).isTrue();
        assertThat(keyVaultService.getDek(newKid)).hasSize(32);
        assertThat(keyVaultService.getHmacKey(newKid)).hasSize(32);

        // Old key versions are still retrievable for historical data decryption
        assertThat(keyVaultService.getDek(oldKid)).isEqualTo(oldDek);
        assertThat(keyVaultService.getHmacKey(oldKid)).isEqualTo(oldHmac);

        Document vaultDoc = mongoTemplate.getDb().getCollection("__lcl_keyvault")
            .find(new Document("_id", "lcl-dek-" + USER_PHONE_NS)).first();
        assertThat(vaultDoc).isNotNull();
        assertThat(vaultDoc.getString("activeKid")).isEqualTo(newKid);

        List<Document> keys = vaultDoc.getList("keys", Document.class);
        assertThat(keys).hasSize(2);
        assertThat(keys.stream().anyMatch(k -> oldKid.equals(k.getString("kid")) && "ROTATED".equals(k.getString("status"))))
            .isTrue();
        assertThat(keys.stream().anyMatch(k -> newKid.equals(k.getString("kid")) && "ACTIVE".equals(k.getString("status"))))
            .isTrue();
    }

    @Test
    @Order(4)
    void rotateDekThrowsWhenVaultDoesNotExist() {
        assertThatThrownBy(() -> keyVaultService.rotateDek(EMPLOYEE_NS))
            .isInstanceOf(FatalCryptoException.class)
            .hasMessageContaining("Vault not found for namespace");
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

    @Test
    @Order(17)
    void collectionEncryptedFieldsSaveReadAndRawBsonShape() {
        IntTestArticle article = new IntTestArticle();
        article.setTitle("Collection Crypto");
        article.setTags(List.of("java", "spring"));
        article.setSettings(Map.of("theme", "dark"));

        articleRepository.save(article);

        IntTestArticle loaded = articleRepository.findById(article.getId()).orElseThrow();
        assertThat(loaded.getTags()).containsExactly("java", "spring");
        assertThat(loaded.getSettings().get("theme")).isEqualTo("dark");

        Document raw = mongoTemplate.getDb().getCollection("intTestArticle")
            .find(new Document("title", "Collection Crypto")).first();
        assertThat(raw).isNotNull();

        Object tagsRaw = raw.get("tags");
        assertThat(tagsRaw).isInstanceOf(List.class);
        List<?> tagsDoc = (List<?>) tagsRaw;
        assertThat(tagsDoc).isNotEmpty();
        assertThat(tagsDoc.get(0)).isInstanceOf(Document.class);
        Document tagSub = (Document) tagsDoc.get(0);
        assertThat(tagSub.get("c")).isNotNull();
        assertThat(tagSub.get("b")).isNotNull();
        assertThat(tagSub.getInteger("_e")).isEqualTo(1);

        Document settingsRaw = raw.get("settings", Document.class);
        assertThat(settingsRaw).isNotNull();
        Document settingSub = settingsRaw.get("theme", Document.class);
        assertThat(settingSub).isNotNull();
        assertThat(settingSub.get("c")).isNotNull();
        assertThat(settingSub.getInteger("_e")).isEqualTo(1);
    }

    @Test
    @Order(18)
    void collectionBlindIndexFindByTagsContainingWorks() {
        IntTestArticle article = new IntTestArticle();
        article.setTitle("Find by tag");
        article.setTags(new ArrayList<>(List.of("security", "crypto")));
        articleRepository.save(article);

        IntTestArticle found = articleRepository.findByTagsContaining("security");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(article.getId());
        assertThat(found.getTags()).contains("security", "crypto");
    }

    @Test
    @Order(19)
    void wholeNestedObjectSaveReadAndRawBsonShape() {
        IntTestUserWithWholeAddress user = new IntTestUserWithWholeAddress();
        user.setName("Whole User");
        IntTestUserWithWholeAddress.Address address = new IntTestUserWithWholeAddress.Address();
        address.setStreet("xx-road");
        address.setCity("shanghai");
        address.setZipCode("200001");
        user.setAddress(address);

        wholeAddressRepository.save(user);

        IntTestUserWithWholeAddress loaded = wholeAddressRepository.findById(user.getId()).orElseThrow();
        assertThat(loaded.getAddress()).isNotNull();
        assertThat(loaded.getAddress().getStreet()).isEqualTo("xx-road");
        assertThat(loaded.getAddress().getCity()).isEqualTo("shanghai");
        assertThat(loaded.getAddress().getZipCode()).isEqualTo("200001");

        Document raw = mongoTemplate.getDb().getCollection("intTestUserWithWholeAddress")
                .find(new Document("name", "Whole User")).first();
        assertThat(raw).isNotNull();

        Object addressRaw = raw.get("address");
        assertThat(addressRaw).isInstanceOf(Document.class);
        Document addressSub = (Document) addressRaw;
        assertThat(addressSub.get("c")).isNotNull();
        assertThat(addressSub.getInteger("_e")).isEqualTo(1);
        assertThat(addressSub.getString("_t")).isEqualTo("DOC");
    }

    @Test
    @Order(20)
    void wholeCollectionSaveReadAndRawBsonShape() {
        IntTestUserWithWholeAddresses user = new IntTestUserWithWholeAddresses();
        user.setName("Whole Addresses User");
        IntTestUserWithWholeAddresses.Address address = new IntTestUserWithWholeAddresses.Address();
        address.setStreet("yy-road");
        address.setCity("beijing");
        user.setAddresses(List.of(address));

        wholeAddressesRepository.save(user);

        IntTestUserWithWholeAddresses loaded = wholeAddressesRepository.findById(user.getId()).orElseThrow();
        assertThat(loaded.getAddresses()).hasSize(1);
        assertThat(loaded.getAddresses().get(0).getStreet()).isEqualTo("yy-road");
        assertThat(loaded.getAddresses().get(0).getCity()).isEqualTo("beijing");

        Document raw = mongoTemplate.getDb().getCollection("intTestUserWithWholeAddresses")
                .find(new Document("name", "Whole Addresses User")).first();
        assertThat(raw).isNotNull();

        Object addressesRaw = raw.get("addresses");
        assertThat(addressesRaw).isInstanceOf(Document.class);
        Document addressesSub = (Document) addressesRaw;
        assertThat(addressesSub.get("c")).isNotNull();
        assertThat(addressesSub.getInteger("_e")).isEqualTo(1);
        assertThat(addressesSub.getString("_t")).isEqualTo("COL");
    }

    @Test
    @Order(21)
    void recursivePojoCollectionStreetEncryptedSaveReadAndRawBsonShape() {
        IntTestUserWithAddresses user = new IntTestUserWithAddresses();
        user.setName("Recursive User");
        IntTestUserWithAddresses.Address address = new IntTestUserWithAddresses.Address();
        address.setStreet("zz-road");
        address.setCity("hangzhou");
        user.setAddresses(List.of(address));

        addressesRepository.save(user);

        IntTestUserWithAddresses loaded = addressesRepository.findById(user.getId()).orElseThrow();
        assertThat(loaded.getAddresses()).hasSize(1);
        assertThat(loaded.getAddresses().get(0).getStreet()).isEqualTo("zz-road");
        assertThat(loaded.getAddresses().get(0).getCity()).isEqualTo("hangzhou");

        Document raw = mongoTemplate.getDb().getCollection("intTestUserWithAddresses")
                .find(new Document("name", "Recursive User")).first();
        assertThat(raw).isNotNull();

        Object addressesRaw = raw.get("addresses");
        assertThat(addressesRaw).isInstanceOf(List.class);
        List<?> rawList = (List<?>) addressesRaw;
        assertThat(rawList).hasSize(1);
        assertThat(rawList.get(0)).isInstanceOf(Document.class);
        Document addressDoc = (Document) rawList.get(0);
        assertThat(addressDoc.get("street")).isInstanceOf(Document.class);
        Document streetSub = (Document) addressDoc.get("street");
        assertThat(streetSub.get("c")).isNotNull();
        assertThat(streetSub.getInteger("_e")).isEqualTo(1);
        assertThat(addressDoc.getString("city")).isEqualTo("hangzhou");
    }

    @Test
    @Order(22)
    void wholeSimpleCollectionAndMapSaveReadAndRawBsonShape() {
        IntTestWholeSimpleCollections entity = new IntTestWholeSimpleCollections();
        entity.setName("Whole Simple");
        entity.setTags(List.of("java", "spring"));
        entity.setSettings(Map.of("theme", "dark"));

        wholeSimpleCollectionsRepository.save(entity);

        IntTestWholeSimpleCollections loaded = wholeSimpleCollectionsRepository.findById(entity.getId()).orElseThrow();
        assertThat(loaded.getTags()).containsExactly("java", "spring");
        assertThat(loaded.getSettings().get("theme")).isEqualTo("dark");

        Document raw = mongoTemplate.getDb().getCollection("intTestWholeSimpleCollections")
                .find(new Document("name", "Whole Simple")).first();
        assertThat(raw).isNotNull();

        assertThat(raw.get("tags")).isInstanceOf(Document.class);
        Document tagsSub = (Document) raw.get("tags");
        assertThat(tagsSub.get("c")).isNotNull();
        assertThat(tagsSub.getInteger("_e")).isEqualTo(1);
        assertThat(tagsSub.getString("_t")).isEqualTo("COL");

        assertThat(raw.get("settings")).isInstanceOf(Document.class);
        Document settingsSub = (Document) raw.get("settings");
        assertThat(settingsSub.get("c")).isNotNull();
        assertThat(settingsSub.getInteger("_e")).isEqualTo(1);
        assertThat(settingsSub.getString("_t")).isEqualTo("MAP");
    }
}
