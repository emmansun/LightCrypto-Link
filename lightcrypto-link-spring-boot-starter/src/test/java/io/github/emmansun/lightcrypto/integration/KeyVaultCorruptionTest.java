package io.github.emmansun.lightcrypto.integration;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for KeyVaultService vault loading, corruption detection,
 * and subsequent startup scenarios in the multi-DEK architecture.
 */
@SpringBootTest(classes = IntTestApplication.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = MongoCiInitializer.class)
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeyVaultCorruptionTest {

    private static final String VAULT_ID = "lcl-dek-IntTestUser";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private KeyVaultService keyVaultService;

    @Autowired
    private CmkProvider cmkProvider;

    @Autowired
    private CryptoProperties properties;

    @Autowired
    private CryptoCodec cryptoCodec;

    @BeforeEach
    void ensureVaultReady() {
        // Ensure vault is initialized for IntTestUser before each test
        keyVaultService.ensureVaultInitialized(IntTestUser.class);
    }

    // ===== 6.7: Subsequent startup loads existing vault without re-generating keys =====

    @Test
    @Order(1)
    void subsequentInitLoadsExistingVault() {
        String activeKid = keyVaultService.getActiveKid(IntTestUser.class);
        byte[] originalDek = keyVaultService.getDek(activeKid).clone();
        byte[] originalHmac = keyVaultService.getHmacKey(activeKid).clone();

        // Create a second KeyVaultService instance with the same dependencies
        // and initialize for the same entity class — it should load the existing vault
        KeyVaultService secondService = new KeyVaultService(
                mongoTemplate, cmkProvider, properties, cryptoCodec);
        secondService.ensureVaultInitialized(IntTestUser.class);

        String secondKid = secondService.getActiveKid(IntTestUser.class);
        assertThat(secondKid).isEqualTo(activeKid);
        assertThat(secondService.getDek(secondKid)).isEqualTo(originalDek);
        assertThat(secondService.getHmacKey(secondKid)).isEqualTo(originalHmac);

        // Verify only one vault document exists for IntTestUser
        Query query = new Query(Criteria.where("_id").is(VAULT_ID));
        long count = mongoTemplate.count(query, "__lcl_keyvault");
        assertThat(count).isEqualTo(1);
    }

    // ===== 6.8: KCV mismatch (corrupt dek.kcv) throws FatalCryptoException =====

    @Test
    @Order(2)
    void kcvMismatchThrowsFatalCryptoException() {
                String activeKid = keyVaultService.getActiveKid(IntTestUser.class);
        // Corrupt the DEK KCV in the first key entry
                Query query = new Query(Criteria.where("_id").is(VAULT_ID).and("keys.kid").is(activeKid));
                Update update = new Update().set("keys.$.dek.kcv", "00000000000000000000000000000000");
        mongoTemplate.updateFirst(query, update, "__lcl_keyvault");

        // A new KeyVaultService should fail to verify the corrupted vault
        KeyVaultService newService = new KeyVaultService(
                mongoTemplate, cmkProvider, properties, cryptoCodec);

        assertThatThrownBy(() -> newService.ensureVaultInitialized(IntTestUser.class))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("KCV mismatch");

        // Restore the vault document for subsequent tests
        restoreVaultKcv();
    }

    // ===== 6.9: Binding mismatch (corrupt binding) throws FatalCryptoException =====

    @Test
    @Order(3)
    void bindingMismatchThrowsFatalCryptoException() {
                String activeKid = keyVaultService.getActiveKid(IntTestUser.class);
        // Corrupt the binding in the first key entry
                Query query = new Query(Criteria.where("_id").is(VAULT_ID).and("keys.kid").is(activeKid));
                Update update = new Update().set("keys.$.binding", "0000000000000000000000000000000000000000000000000000000000000000");
        mongoTemplate.updateFirst(query, update, "__lcl_keyvault");

        // A new KeyVaultService should fail to verify the corrupted vault
        KeyVaultService newService = new KeyVaultService(
                mongoTemplate, cmkProvider, properties, cryptoCodec);

        assertThatThrownBy(() -> newService.ensureVaultInitialized(IntTestUser.class))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("binding mismatch");

        // Restore the vault document for subsequent tests
        restoreVaultBinding();
    }

    // ===== 11.7: KCV corruption integration — vault integrity check end-to-end =====

    @Test
    @Order(4)
    void kcvCorruptionDetectedOnVaultReload() {
        // Verify the vault is currently healthy
        KeyVaultService healthyService = new KeyVaultService(
                mongoTemplate, cmkProvider, properties, cryptoCodec);
        assertThatNoException().isThrownBy(() -> healthyService.ensureVaultInitialized(IntTestUser.class));
        String kid = healthyService.getActiveKid(IntTestUser.class);
        assertThat(healthyService.getDek(kid)).hasSize(32);

        // Corrupt the HMAC key KCV (different from DEK KCV in task 6.8)
        Query query = new Query(Criteria.where("_id").is(VAULT_ID).and("keys.kid").is(kid));
        Update update = new Update().set("keys.$.hmk.kcv", "deadbeef000000000000000000000000");
        mongoTemplate.updateFirst(query, update, "__lcl_keyvault");

        // Verify corruption is detected
        KeyVaultService corruptService = new KeyVaultService(
                mongoTemplate, cmkProvider, properties, cryptoCodec);
        assertThatThrownBy(() -> corruptService.ensureVaultInitialized(IntTestUser.class))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("KCV mismatch");

        // Restore vault for any remaining tests
        restoreVaultKcv();
    }

    // ===== 11.10: Concurrent rotation conflict uses optimistic locking =====

    @Test
    @Order(5)
    void staleVaultSnapshotDoesNotOverwriteRotatedVault() {
        Query query = new Query(Criteria.where("_id").is(VAULT_ID));
        Document staleVault = mongoTemplate.getDb().getCollection("__lcl_keyvault")
                .find(query.getQueryObject())
                .first();
        assertThat(staleVault).isNotNull();

        String staleActiveKid = staleVault.getString("activeKid");
        Integer staleVersion = staleVault.getInteger("v");

        keyVaultService.rotateDek(IntTestUser.class);

        Document currentVault = mongoTemplate.getDb().getCollection("__lcl_keyvault")
                .find(query.getQueryObject())
                .first();
        assertThat(currentVault).isNotNull();
        assertThat(currentVault.getString("activeKid")).isNotEqualTo(staleActiveKid);
        assertThat(currentVault.getInteger("v")).isEqualTo(staleVersion + 1);

        Document staleFilter = new Document("_id", VAULT_ID)
                .append("activeKid", staleActiveKid)
                .append("v", staleVersion);

        UpdateResult result = mongoTemplate.getDb()
                .getCollection("__lcl_keyvault")
                .replaceOne(staleFilter, staleVault);

        assertThat(result.getMatchedCount()).isZero();
    }

    // ===== TTL cache expiry integration test =====

    @Test
    @Order(6)
    void cacheExpiryTriggersReloadAndZerosOldKeyMaterial() throws Exception {
        // Create a service with a very short TTL (100ms)
        CryptoProperties shortTtlProps = new CryptoProperties();
        shortTtlProps.setCmk(properties.getCmk());
        shortTtlProps.setCacheTtl(Duration.ofMillis(100));

        KeyVaultService shortTtlService = new KeyVaultService(
                mongoTemplate, cmkProvider, shortTtlProps, cryptoCodec);
        shortTtlService.ensureVaultInitialized(IntTestUser.class);

        // Grab references to the cached key material
        String kid = shortTtlService.getActiveKid(IntTestUser.class);
        byte[] dekRef = shortTtlService.getDek(kid);
        byte[] hmacRef = shortTtlService.getHmacKey(kid);
        assertThat(dekRef).hasSize(32);

        // Wait for TTL to expire
        Thread.sleep(200);

        // Trigger reload — this should evict the old entry and reload from MongoDB
        shortTtlService.ensureVaultInitialized(IntTestUser.class);

        // Old key material should be zeroed
        assertThat(dekRef).containsOnly((byte) 0);
        assertThat(hmacRef).containsOnly((byte) 0);

        // New entry should be valid
        String newKid = shortTtlService.getActiveKid(IntTestUser.class);
        assertThat(shortTtlService.getDek(newKid)).hasSize(32);
        assertThat(shortTtlService.getDek(newKid)).isNotEqualTo(new byte[32]); // not all zeros
    }

    // ===== Helper methods =====

    /**
     * Restores the vault document's KCV fields by re-computing them from the
     * unwrapped keys held by the original KeyVaultService.
     */
    private void restoreVaultKcv() {
        String activeKid = keyVaultService.getActiveKid(IntTestUser.class);
        String correctDekKcv = cryptoCodec.computeKcv(keyVaultService.getDek(activeKid));
        String correctHmacKcv = cryptoCodec.computeKcv(keyVaultService.getHmacKey(activeKid));

        Query query = new Query(Criteria.where("_id").is(VAULT_ID).and("keys.kid").is(activeKid));
        Update update = new Update()
                .set("keys.$.dek.kcv", correctDekKcv)
                .set("keys.$.hmk.kcv", correctHmacKcv);
        mongoTemplate.updateFirst(query, update, "__lcl_keyvault");
    }

    /**
     * Restores the vault document's binding field by re-computing it from the
     * unwrapped keys held by the original KeyVaultService.
     */
    private void restoreVaultBinding() {
        String activeKid = keyVaultService.getActiveKid(IntTestUser.class);
        String correctBinding = cryptoCodec.computeBinding(
                keyVaultService.getHmacKey(activeKid), keyVaultService.getDek(activeKid));
                Query query = new Query(Criteria.where("_id").is(VAULT_ID).and("keys.kid").is(activeKid));
                Update update = new Update().set("keys.$.binding", correctBinding);
        mongoTemplate.updateFirst(query, update, "__lcl_keyvault");
    }
}
