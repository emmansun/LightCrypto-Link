package io.github.emmansun.lightcrypto.integration;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
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
 * and subsequent startup scenarios in the namespace-based multi-DEK architecture.
 */
@SpringBootTest(classes = IntTestApplication.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = MongoCiInitializer.class)
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeyVaultCorruptionTest {

    private static final String TEST_NAMESPACE = "default.default.IntTestUser#phone";
    private static final String VAULT_ID = "lcl-dek-" + TEST_NAMESPACE;
    private static final AlgorithmId KCV_ALGORITHM = AlgorithmId.AES_256_GCM;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private KeyVaultService keyVaultService;

    @Autowired
    private CmkProvider cmkProvider;

    @Autowired
    private CryptoProperties properties;

    @BeforeEach
    void ensureVaultReady() {
        keyVaultService.ensureVaultInitialized(TEST_NAMESPACE);
    }

    // ===== Subsequent startup loads existing vault without re-generating keys =====

    @Test
    @Order(1)
    void subsequentInitLoadsExistingVault() {
        String activeKid = keyVaultService.getActiveKid(TEST_NAMESPACE);
        byte[] originalDek = keyVaultService.getDek(activeKid).clone();
        byte[] originalHmac = keyVaultService.getHmacKey(activeKid).clone();

        // Create a second KeyVaultService instance with the same dependencies
        KeyVaultService secondService = new KeyVaultService(mongoTemplate, cmkProvider, properties);
        secondService.ensureVaultInitialized(TEST_NAMESPACE);

        String secondKid = secondService.getActiveKid(TEST_NAMESPACE);
        assertThat(secondKid).isEqualTo(activeKid);
        assertThat(secondService.getDek(secondKid)).isEqualTo(originalDek);
        assertThat(secondService.getHmacKey(secondKid)).isEqualTo(originalHmac);

        // Verify only one vault document exists for this namespace
        Query query = new Query(Criteria.where("_id").is(VAULT_ID));
        long count = mongoTemplate.count(query, "__lcl_keyvault");
        assertThat(count).isEqualTo(1);
    }

    // ===== KCV mismatch (corrupt dek.kcv) throws FatalCryptoException =====

    @Test
    @Order(2)
    void kcvMismatchThrowsFatalCryptoException() {
        String activeKid = keyVaultService.getActiveKid(TEST_NAMESPACE);
        Query query = new Query(Criteria.where("_id").is(VAULT_ID).and("keys.kid").is(activeKid));
        Update update = new Update().set("keys.$.dek.kcv", "00000000000000000000000000000000");
        mongoTemplate.updateFirst(query, update, "__lcl_keyvault");

        // A new KeyVaultService should fail to verify the corrupted vault
        KeyVaultService newService = new KeyVaultService(mongoTemplate, cmkProvider, properties);

        assertThatThrownBy(() -> newService.ensureVaultInitialized(TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("KCV mismatch");

        restoreVaultKcv();
    }

    // ===== Binding mismatch (corrupt binding) throws FatalCryptoException =====

    @Test
    @Order(3)
    void bindingMismatchThrowsFatalCryptoException() {
        String activeKid = keyVaultService.getActiveKid(TEST_NAMESPACE);
        Query query = new Query(Criteria.where("_id").is(VAULT_ID).and("keys.kid").is(activeKid));
        Update update = new Update().set("keys.$.binding", "0000000000000000000000000000000000000000000000000000000000000000");
        mongoTemplate.updateFirst(query, update, "__lcl_keyvault");

        KeyVaultService newService = new KeyVaultService(mongoTemplate, cmkProvider, properties);

        assertThatThrownBy(() -> newService.ensureVaultInitialized(TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("binding mismatch");

        restoreVaultBinding();
    }

    // ===== KCV corruption integration — vault integrity check end-to-end =====

    @Test
    @Order(4)
    void kcvCorruptionDetectedOnVaultReload() {
        KeyVaultService healthyService = new KeyVaultService(mongoTemplate, cmkProvider, properties);
        assertThatNoException().isThrownBy(() -> healthyService.ensureVaultInitialized(TEST_NAMESPACE));
        String kid = healthyService.getActiveKid(TEST_NAMESPACE);
        assertThat(healthyService.getDek(kid)).hasSize(32);

        // Corrupt the HMAC key KCV
        Query query = new Query(Criteria.where("_id").is(VAULT_ID).and("keys.kid").is(kid));
        Update update = new Update().set("keys.$.hmk.kcv", "deadbeef000000000000000000000000");
        mongoTemplate.updateFirst(query, update, "__lcl_keyvault");

        KeyVaultService corruptService = new KeyVaultService(mongoTemplate, cmkProvider, properties);
        assertThatThrownBy(() -> corruptService.ensureVaultInitialized(TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("KCV mismatch");

        restoreVaultKcv();
    }

    // ===== Concurrent rotation conflict uses optimistic locking =====

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

        keyVaultService.rotateDek(TEST_NAMESPACE);

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
        CryptoProperties shortTtlProps = new CryptoProperties();
        shortTtlProps.setCmk(properties.getCmk());
        shortTtlProps.setCacheTtl(Duration.ofMillis(100));

        KeyVaultService shortTtlService = new KeyVaultService(mongoTemplate, cmkProvider, shortTtlProps);
        shortTtlService.ensureVaultInitialized(TEST_NAMESPACE);

        String kid = shortTtlService.getActiveKid(TEST_NAMESPACE);
        byte[] dekRef = shortTtlService.getDek(kid);
        byte[] hmacRef = shortTtlService.getHmacKey(kid);
        assertThat(dekRef).hasSize(32);

        // Wait for TTL to expire
        Thread.sleep(200);

        // Trigger reload
        shortTtlService.ensureVaultInitialized(TEST_NAMESPACE);

        // Old key material should be zeroed
        assertThat(dekRef).containsOnly((byte) 0);
        assertThat(hmacRef).containsOnly((byte) 0);

        // New entry should be valid
        String newKid = shortTtlService.getActiveKid(TEST_NAMESPACE);
        assertThat(shortTtlService.getDek(newKid)).hasSize(32);
        assertThat(shortTtlService.getDek(newKid)).isNotEqualTo(new byte[32]);
    }

    // ===== Helper methods =====

    private void restoreVaultKcv() {
        String activeKid = keyVaultService.getActiveKid(TEST_NAMESPACE);
        String correctDekKcv = KeyCheckValue.computeDekKcv(keyVaultService.getDek(activeKid), KCV_ALGORITHM);
        String correctHmacKcv = KeyCheckValue.computeHmacKcv(keyVaultService.getHmacKey(activeKid));

        Query query = new Query(Criteria.where("_id").is(VAULT_ID).and("keys.kid").is(activeKid));
        Update update = new Update()
                .set("keys.$.dek.kcv", correctDekKcv)
                .set("keys.$.hmk.kcv", correctHmacKcv);
        mongoTemplate.updateFirst(query, update, "__lcl_keyvault");
    }

    private void restoreVaultBinding() {
        String activeKid = keyVaultService.getActiveKid(TEST_NAMESPACE);
        String correctBinding = KeyCheckValue.computeBinding(
                keyVaultService.getHmacKey(activeKid), keyVaultService.getDek(activeKid));
        Query query = new Query(Criteria.where("_id").is(VAULT_ID).and("keys.kid").is(activeKid));
        Update update = new Update().set("keys.$.binding", correctBinding);
        mongoTemplate.updateFirst(query, update, "__lcl_keyvault");
    }
}
